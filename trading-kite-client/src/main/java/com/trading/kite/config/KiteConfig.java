package com.trading.kite.config;

import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Wires up the Kite Connect SDK as a Spring-managed bean.
 *
 * KiteConnect is NOT thread-safe for session mutation,
 * so we keep one instance and set the access token on it
 * after the daily auth flow completes.
 */
@Slf4j
@Configuration
@EnableRetry
@RequiredArgsConstructor
public class KiteConfig {

    private final KiteProperties kiteProperties;

    /**
     * Primary KiteConnect instance shared across the application.
     *
     * Access token is NOT set here — it's set by KiteAuthService
     * after the OAuth flow completes (or on app startup if already stored).
     */
    @Bean
    public KiteConnect kiteConnect() {
        KiteConnect kiteConnect = new KiteConnect(kiteProperties.getApiKey());
        kiteConnect.setSessionExpiryHook(() ->
            log.warn("Kite session expired — access token needs refresh. " +
                     "Trigger login flow at: /api/v1/auth/login")
        );

        // If access token is already in config (dev shortcut), set it now
        String storedToken = kiteProperties.getAccessToken();
        if (storedToken != null && !storedToken.isBlank()) {
            kiteConnect.setAccessToken(storedToken);
            log.info("KiteConnect initialised with stored access token");
        } else {
            log.warn("No access token configured. Complete login at: /api/v1/auth/login");
        }

        return kiteConnect;
    }
}
