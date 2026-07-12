package com.trading.api.controller;

import com.trading.api.dto.ApiResponse;
import com.trading.kite.service.InstrumentRegistry;
import com.trading.kite.service.KiteAuthService;
import com.trading.kite.service.KiteTickerService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Handles the Kite Connect OAuth 2.0 authentication flow.
 *
 * FLOW:
 *  1. User hits GET /api/v1/auth/login
 *     → App generates Kite login URL and redirects user there
 *
 *  2. User logs in on kite.trade (Zerodha credentials + TOTP)
 *     → Kite redirects to our callback URL with request_token
 *
 *  3. GET /api/v1/auth/callback?request_token=xxx&status=success
 *     → We exchange request_token for access_token
 *     → access_token is set on KiteConnect bean
 *     → WebSocket tick stream is started
 *     → Done! App is live.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KiteAuthService kiteAuthService;
    private final KiteTickerService kiteTickerService;
    private final InstrumentRegistry instrumentRegistry;

    /**
     * Step 1: Redirect user to Kite's login page.
     *
     * Open this in a browser — it will redirect you to kite.trade login.
     * After login, Kite automatically calls /callback with the request_token.
     *
     * GET http://localhost:8080/api/v1/auth/login
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String loginUrl = kiteAuthService.getLoginUrl();
        log.info("Redirecting to Kite login: {}", loginUrl);
        return ResponseEntity.status(302)
            .location(URI.create(loginUrl))
            .build();
    }

    /**
     * Step 2: Kite calls this after user login with a one-time request_token.
     *
     * Kite appends:
     *   ?request_token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx&action=login&status=success
     *
     * We exchange it for a persistent access_token, then start the WebSocket.
     *
     * GET http://localhost:8080/api/v1/auth/callback?request_token=xxx&status=success
     */
    @GetMapping("/callback")
    public ResponseEntity<ApiResponse<Map<String, String>>> callback(
        @RequestParam("request_token") String requestToken,
        @RequestParam(value = "status", defaultValue = "unknown") String status
    ) {
        log.info("Kite callback received | status={} | token_prefix={}...",
            status, requestToken.substring(0, Math.min(8, requestToken.length())));

        if (!"success".equalsIgnoreCase(status)) {
            log.error("Kite login failed with status: {}", status);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Kite login failed with status: " + status));
        }

        try {
            String accessToken = kiteAuthService.generateSession(requestToken);

            // Start the WebSocket tick stream now that we have a valid session
            if (!kiteTickerService.isConnected()) {
                log.info("Starting KiteTicker WebSocket after successful auth...");
                kiteTickerService.connect();
            }
            instrumentRegistry.load();

            log.info("✅ Authentication complete. Tick stream starting.");

            return ResponseEntity.status(302)
                    .location(URI.create("http://localhost:5173?auth=success"))
                    .build();

        } catch (Exception | KiteException e) {
            log.error("Session generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to generate session: " + e.getMessage()));
        }
    }

    /**
     * Quick status check — is the app authenticated with Kite?
     *
     * GET http://localhost:8080/api/v1/auth/status
     *
     * Response:
     *   { "success": true, "data": { "authenticated": true, "tickerConnected": true } }
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "authenticated",   kiteAuthService.isAuthenticated(),
            "tickerConnected", kiteTickerService.isConnected()
        )));
    }

    @GetMapping("/instruments/test")
    public ResponseEntity<ApiResponse<Map<String, String>>> testInstrument(
            @RequestParam Long token
    ) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "token", String.valueOf(token),
                "symbol", instrumentRegistry.getSymbol(token) != null
                        ? instrumentRegistry.getSymbol(token) : "NOT FOUND",
                "exchange", instrumentRegistry.getExchange(token) != null
                        ? instrumentRegistry.getExchange(token) : "NOT FOUND",
                "registrySize", String.valueOf(instrumentRegistry.size())
        )));
    }
}
