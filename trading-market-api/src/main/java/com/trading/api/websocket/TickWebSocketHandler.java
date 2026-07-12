package com.trading.api.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all browser WebSocket connections and pushes live ticks.
 *
 * SESSION LIFECYCLE:
 * ──────────────────────────────────────────────────────────────
 * Browser connects   → afterConnectionEstablished() → session stored
 * Browser sends msg  → handleTextMessage()           → parse subscription
 * Tick arrives       → broadcastTick()               → push to subscribed sessions
 * Browser closes     → afterConnectionClosed()        → session removed
 * ──────────────────────────────────────────────────────────────
 *
 * SUBSCRIPTION PROTOCOL:
 * Browser sends JSON to subscribe/unsubscribe:
 *   {"action": "subscribe",   "tokens": [256265, 408065]}
 *   {"action": "unsubscribe", "tokens": [256265]}
 *
 * Server pushes tick JSON on every update:
 *   {"instrumentToken": 256265, "lastPrice": 24052.5, ...}
 *
 * DATA STRUCTURES:
 * sessions     — all connected WebSocket sessions
 *               ConcurrentHashMap because sessions connect/disconnect
 *               on different threads simultaneously
 *
 * subscriptions — maps instrumentToken → Set of sessions subscribed to it
 *               When a tick for token 256265 arrives, we look up
 *               subscriptions.get(256265) and push to those sessions only
 *               (not ALL connected sessions — someone watching RELIANCE
 *               doesn't need NIFTY ticks)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TickWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // All active browser sessions
    // Key: session ID, Value: WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Token → Set of sessions subscribed to that token
    private final Map<Long, Set<String>> subscriptions = new ConcurrentHashMap<>();

    // ── Connection lifecycle ───────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket client connected: {} | total sessions: {}",
            session.getId(), sessions.size());

        // Send a welcome message so the client knows it's connected
        sendToSession(session, Map.of(
            "type", "connected",
            "message", "WebSocket connected. Send subscription message to receive ticks.",
            "sessionId", session.getId()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Remove from all subscription sets
        subscriptions.values().forEach(set -> set.remove(session.getId()));

        // Remove empty subscription sets to avoid memory leak
        subscriptions.entrySet().removeIf(e -> e.getValue().isEmpty());

        // Remove the session itself
        sessions.remove(session.getId());

        log.info("WebSocket client disconnected: {} | reason: {} | remaining: {}",
            session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("WebSocket transport error for session {}: {}",
            session.getId(), ex.getMessage());
        sessions.remove(session.getId());
    }

    // ── Message handling ───────────────────────────────────────────────────

    /**
     * Handles subscription messages from the browser.
     *
     * Expected message format:
     * {"action": "subscribe",   "tokens": [256265, 408065, 738561]}
     * {"action": "unsubscribe", "tokens": [256265]}
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String action = json.get("action").asText();
            JsonNode tokens = json.get("tokens");

            if (tokens == null || !tokens.isArray()) {
                sendToSession(session, Map.of("type", "error",
                    "message", "Invalid message format. Expected: {action, tokens[]}"));
                return;
            }

            if ("subscribe".equals(action)) {
                tokens.forEach(token -> subscribe(session, token.asLong()));
                sendToSession(session, Map.of(
                    "type", "subscribed",
                    "tokens", tokens
                ));
                log.info("Session {} subscribed to {} tokens", session.getId(), tokens.size());

            } else if ("unsubscribe".equals(action)) {
                tokens.forEach(token -> unsubscribe(session, token.asLong()));
                sendToSession(session, Map.of(
                    "type", "unsubscribed",
                    "tokens", tokens
                ));

            } else {
                sendToSession(session, Map.of("type", "error",
                    "message", "Unknown action: " + action));
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage());
            sendToSession(session, Map.of("type", "error",
                "message", "Failed to parse message: " + e.getMessage()));
        }
    }

    // ── Tick broadcasting ──────────────────────────────────────────────────

    /**
     * Called by TickBroadcastService when a new tick arrives from Kafka.
     * Pushes the tick only to sessions subscribed to that instrument token.
     *
     * This runs on the Kafka consumer thread — must be thread-safe.
     * ConcurrentHashMap + synchronized WebSocket send handles this.
     */
    public void broadcastTick(TickDto tick) {
        Set<String> subscribedSessionIds = subscriptions.get(tick.getInstrumentToken());

        if (subscribedSessionIds == null || subscribedSessionIds.isEmpty()) {
            return; // nobody subscribed to this token — skip
        }

        // Build the message once, send to all subscribed sessions
        String message;
        try {
            message = objectMapper.writeValueAsString(tick);
        } catch (Exception e) {
            log.error("Failed to serialize tick for token {}: {}",
                tick.getInstrumentToken(), e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(message);

        subscribedSessionIds.forEach(sessionId -> {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    // synchronized on session because WebSocket sends
                    // must not happen concurrently on the same session
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send tick to session {}: {}",
                        sessionId, e.getMessage());
                    // Don't remove here — let afterConnectionClosed handle cleanup
                }
            }
        });
    }

    /**
     * Returns current connection stats — useful for the /auth/status endpoint.
     */
    public int getConnectedSessionCount() {
        return sessions.size();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void subscribe(WebSocketSession session, long token) {
        subscriptions
            .computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet())
            .add(session.getId());
    }

    private void unsubscribe(WebSocketSession session, long token) {
        Set<String> set = subscriptions.get(token);
        if (set != null) {
            set.remove(session.getId());
        }
    }

    private void sendToSession(WebSocketSession session, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("Failed to send message to session {}: {}",
                session.getId(), e.getMessage());
        }
    }
}