package com.trading.kite.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import com.trading.kite.config.KiteProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles the Kite Connect OAuth 2.0 flow.
 *
 * FLOW OVERVIEW:
 * ─────────────────────────────────────────────────────────────────
 *  1. User hits  GET /api/v1/auth/login
 *  2. We redirect them to Kite's login URL (generated here)
 *  3. User logs in on kite.trade
 *  4. Kite redirects to our redirectUrl with ?request_token=xxx&status=success
 *  5. We call generateSession(request_token) → get back access_token
 *  6. We set access_token on the KiteConnect bean → all subsequent API calls work
 * ─────────────────────────────────────────────────────────────────
 *
 * The access_token is valid until 6:00 AM IST next day.
 * After that, the session expiry hook fires and the flow must repeat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiteAuthService {

    private final KiteConnect kiteConnect;
    private final KiteProperties kiteProperties;

    // Holds the current access token in memory (also update Redis in prod)
    private volatile String currentAccessToken;

    /**
     * Returns the Kite login URL to redirect the user to.
     *
     * Example output:
     * https://kite.trade/connect/login?api_key=ab12cd34&v=3
     */
    public String getLoginUrl() {
        String loginUrl = kiteConnect.getLoginURL();
        log.info("Generated Kite login URL: {}", loginUrl);
        return loginUrl;
    }

    /**
     * Exchanges a one-time request_token (from Kite's redirect) for a
     * persistent access_token. Sets it on the shared KiteConnect bean
     * so all downstream services can make authenticated API calls.
     *
     * @param requestToken  the token from Kite's redirect query param
     * @return              the access token string (store this for the day)
     * @throws Exception    if Kite rejects the token (wrong secret, expired, reused)
     */
    public String generateSession(String requestToken) throws Exception, KiteException {
        log.info("Exchanging request_token for access_token...");

        User user = kiteConnect.generateSession(requestToken, kiteProperties.getApiSecret());

        String accessToken = user.accessToken;
        kiteConnect.setAccessToken(accessToken);
        kiteConnect.setUserId(user.userId);
        this.currentAccessToken = accessToken;

        log.info("Session established for user: {} ({})", user.userName, user.email);
        log.info("Access token set. Valid until 6:00 AM IST tomorrow.");
        log.debug("Access token: {}", accessToken); // only visible at DEBUG level

        return accessToken;
    }

    /**
     * Returns the currently active access token, or null if not yet authenticated.
     * Use this to persist the token to Redis or DB for restart recovery.
     */
    public String getCurrentAccessToken() {
        return currentAccessToken;
    }

    /**
     * Checks if the KiteConnect instance currently has an access token set.
     * Does NOT validate with Kite servers — just a local null check.
     */
    public boolean isAuthenticated() {
        return currentAccessToken != null && !currentAccessToken.isBlank();
    }

    /**
     * Manually set an access token (e.g. loaded from Redis on app restart).
     * Useful when the app restarts mid-day and the token is still valid.
     */
    public void restoreSession(String accessToken) {
        kiteConnect.setAccessToken(accessToken);
        this.currentAccessToken = accessToken;
        log.info("Session restored from stored access token.");
    }
}
