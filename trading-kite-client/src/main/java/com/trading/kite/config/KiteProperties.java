package com.trading.kite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds all kite.* properties from application.yml into a single typed object.
 * Never hardcode api-key or api-secret — always read from config.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kite")
public class KiteProperties {

    /** From developers.kite.trade → your app's api_key */
    private String apiKey;

    /** From developers.kite.trade → your app's api_secret */
    private String apiSecret;

    /**
     * Access token obtained after login flow.
     * In dev: set manually in application-dev.yml after running the auth flow once.
     * In prod: stored in Redis and refreshed daily at 6:05 AM IST.
     */
    private String accessToken;

    /**
     * Where Kite redirects after user login.
     * Must exactly match what you entered in the Kite app settings.
     * e.g. http://localhost:8080/api/v1/auth/callback
     */
    private String redirectUrl;

    /** Kite Connect base URL — unlikely to change */
    private String baseUrl = "https://api.kite.trade";

    /** WebSocket tick stream URL */
    private String wsUrl = "wss://ws.kite.trade";

    /**
     * Instrument tokens to subscribe to on startup.
     * NSE:NIFTY 50 index = 256265, add your watchlist tokens here.
     * e.g. 256265, 408065 (RELIANCE), 738561 (INFY)
     */
    private java.util.List<Long> defaultSubscriptions = new java.util.ArrayList<>();
}
