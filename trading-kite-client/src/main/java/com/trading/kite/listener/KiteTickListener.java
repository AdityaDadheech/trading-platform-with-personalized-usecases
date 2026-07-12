package com.trading.kite.listener;

import com.zerodhatech.models.Tick;
import java.util.ArrayList;

/**
 * Listener interface that decouples tick event handling from the Kite SDK.
 *
 * WHY THIS EXISTS:
 * The Kite SDK fires callbacks directly on the WebSocket thread.
 * Instead of letting SDK callbacks reach Kafka publishers directly,
 * we define this interface so the Ingestion module can implement it
 * and handle ticks in its own way (normalize → publish to Kafka).
 *
 * This keeps trading-kite-client free of any Kafka/Spring Event dependency.
 */
public interface KiteTickListener {

    /**
     * Called for every tick batch received from Kite WebSocket.
     * Typically fires every ~200ms during market hours.
     *
     * @param ticks list of ticks (one per subscribed instrument)
     */
    void onTicks(ArrayList<Tick> ticks);

    /**
     * Called when the WebSocket connection is successfully established.
     * Good place to log connection status or update a health indicator.
     */
    void onConnected();

    /**
     * Called when the WebSocket disconnects (network drop, session expiry, etc.)
     * KiteTickerService handles reconnection — this is just for notification.
     */
    void onDisconnected();

    /**
     * Called on any WebSocket or SDK error.
     * @param exception the error that occurred
     */
    void onError(Exception exception);
}
