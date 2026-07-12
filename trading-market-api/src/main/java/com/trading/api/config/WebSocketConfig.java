package com.trading.api.config;

import com.trading.api.websocket.TickWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers WebSocket endpoints with Spring.
 *
 * WHY RAW WEBSOCKET INSTEAD OF STOMP/SockJS:
 * STOMP is a messaging protocol on top of WebSocket — great for
 * complex pub/sub but adds overhead and complexity.
 * For our use case (push tick data to browser) raw WebSocket
 * is simpler, faster, and easier to consume from React.
 *
 * setAllowedOrigins("*") allows the React dev server on port 5173
 * to connect. In production, restrict to your actual domain.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TickWebSocketHandler tickWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tickWebSocketHandler, "/ws/ticks")
            .setAllowedOrigins("*");
    }
}