package com.trading.kite.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.models.Tick;
import com.trading.kite.config.KiteProperties;
import com.trading.kite.listener.KiteTickListener;
import com.zerodhatech.ticker.OnError;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Kite WebSocket (KiteTicker) lifecycle.
 *
 * Responsibilities:
 *  - Connect / disconnect the WebSocket
 *  - Subscribe / unsubscribe instrument tokens
 *  - Set tick mode (LTP / QUOTE / FULL)
 *  - Wire Kite SDK callbacks → our KiteTickListener
 *  - Auto-reconnect with exponential backoff (via Spring Retry)
 *
 * TICK MODES:
 *  - KiteTicker.MODE_LTP   → just last traded price (lightest)
 *  - KiteTicker.MODE_QUOTE → LTP + OHLC + volume + bid/ask (recommended)
 *  - KiteTicker.MODE_FULL  → everything including market depth (heaviest)
 *
 * We use MODE_QUOTE for Phase 1. Switch to MODE_FULL when you need depth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiteTickerService {

    private final KiteConnect kiteConnect;
    private final KiteProperties kiteProperties;
    private final List<KiteTickListener> tickListeners; // Spring injects all beans implementing this

    private KiteTicker kiteTicker;
    private volatile boolean connected = false;

    /**
     * Initialises and connects the WebSocket.
     * Called by KiteAuthService after a successful session is established,
     * OR on app startup if a stored token is restored.
     *
     * @Retryable: if the initial connection fails (network blip, token not ready),
     * retry up to 3 times with 2s → 4s → 8s backoff.
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void connect() throws KiteException {
        if (connected) {
            log.info("KiteTicker already connected. Skipping.");
            return;
        }

        log.info("Initialising KiteTicker WebSocket...");
        String accessToken = kiteConnect.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "No access token on KiteConnect bean. Complete login at: GET /api/v1/auth/login"
            );
        }
        kiteTicker = new KiteTicker(accessToken, kiteProperties.getApiKey());

        // Wire up all SDK callbacks
        kiteTicker.setOnConnectedListener(this::handleConnected);
        kiteTicker.setOnDisconnectedListener(this::handleDisconnected);
        kiteTicker.setOnTickerArrivalListener(this::handleTicks);
        kiteTicker.setOnErrorListener(new OnError() {
            @Override
            public void onError(String var) {
                handleError(new RuntimeException("WebSocket connection error"));
            }

            @Override
            public void onError(Exception e) {
                handleError(e);
            }

            @Override
            public void onError(KiteException e) {
                handleError(new Exception("Kite Exception error"));
            }
        });
        kiteTicker.setOnOrderUpdateListener(order ->
            log.debug("Order update received: {}", order.orderId)
        );

        // Enable SDK-level auto-reconnect (separate from our Spring Retry above)
        kiteTicker.setTryReconnection(true);
        kiteTicker.setMaximumRetries(10);
        kiteTicker.setMaximumRetryInterval(30); // seconds

        kiteTicker.connect();
        log.info("KiteTicker connect() called — waiting for onConnected callback...");
    }

    /**
     * Subscribe a list of instrument tokens and set them to QUOTE mode.
     * Safe to call multiple times — just adds to existing subscriptions.
     *
     * @param tokens Kite instrument tokens (e.g. 256265 for NIFTY 50)
     */
    public void subscribe(List<Long> tokens) {
        if (kiteTicker == null || !connected) {
            log.warn("Cannot subscribe — ticker not connected yet. Tokens will be lost: {}", tokens);
            return;
        }
        ArrayList<Long> tokenList = new ArrayList<>(tokens);
        kiteTicker.subscribe(tokenList);
        kiteTicker.setMode(tokenList, KiteTicker.modeQuote);
        log.info("Subscribed to {} instruments in QUOTE mode: {}", tokens.size(), tokens);
    }

    /**
     * Unsubscribe specific tokens (e.g. user removes from watchlist).
     */
    public void unsubscribe(List<Long> tokens) {
        if (kiteTicker == null || !connected) return;
        kiteTicker.unsubscribe(new ArrayList<>(tokens));
        log.info("Unsubscribed from tokens: {}", tokens);
    }

    /**
     * Disconnect gracefully. Called on app shutdown via @PreDestroy.
     */
    @PreDestroy
    public void disconnect() {
        if (kiteTicker != null && connected) {
            kiteTicker.disconnect();
            connected = false;
            log.info("KiteTicker disconnected cleanly.");
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ─── Internal SDK callbacks ────────────────────────────────────────────────

    private void handleConnected() {
        connected = true;
        log.info("✅ KiteTicker WebSocket connected successfully.");

        // Subscribe to default instruments from config
        List<Long> defaults = kiteProperties.getDefaultSubscriptions();
        if (!defaults.isEmpty()) {
            subscribe(defaults);
        }

        // Notify all listeners
        tickListeners.forEach(listener -> {
            try {
                listener.onConnected();
            } catch (Exception e) {
                log.error("Error in KiteTickListener.onConnected()", e);
            }
        });
    }

    private void handleDisconnected() {
        connected = false;
        log.warn("⚠️ KiteTicker WebSocket disconnected. SDK will attempt reconnect...");
        tickListeners.forEach(listener -> {
            try {
                listener.onDisconnected();
            } catch (Exception e) {
                log.error("Error in KiteTickListener.onDisconnected()", e);
            }
        });
    }

    private void handleTicks(ArrayList<Tick> ticks) {
        if (ticks == null || ticks.isEmpty()) return;
        // Fan out to all registered listeners (ingestion service implements this)
        tickListeners.forEach(listener -> {
            try {
                listener.onTicks(ticks);
            } catch (Exception e) {
                log.error("Error in KiteTickListener.onTicks()", e);
            }
        });
    }

    private void handleError(Exception exception) {
        log.error("KiteTicker error: {}", exception.getMessage(), exception);
        tickListeners.forEach(listener -> {
            try {
                listener.onError(exception);
            } catch (Exception e) {
                log.error("Error in KiteTickListener.onError()", e);
            }
        });
    }
}
