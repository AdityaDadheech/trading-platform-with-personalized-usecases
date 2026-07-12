package com.trading.common.constants;

/**
 * Redis cache key patterns.
 * Use String.format(CacheKeys.LTP, instrumentToken) to build actual keys.
 */
public final class CacheKeys {

    private CacheKeys() {}

    /** Last traded price per instrument: "ltp:256265" */
    public static final String LTP = "ltp:%d";

    /** Latest full tick per instrument: "tick:256265" */
    public static final String TICK = "tick:%d";

    /** Kite access token for the day: "kite:access_token" */
    public static final String KITE_ACCESS_TOKEN = "kite:access_token";

    /** Instrument list last sync timestamp */
    public static final String INSTRUMENTS_LAST_SYNC = "instruments:last_sync";
}
