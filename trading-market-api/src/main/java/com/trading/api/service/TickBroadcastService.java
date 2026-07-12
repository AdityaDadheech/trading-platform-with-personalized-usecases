package com.trading.api.service;

import com.trading.api.websocket.TickWebSocketHandler;
import com.trading.common.constants.CacheKeys;
import com.trading.common.constants.KafkaTopics;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Consumes ticks from Kafka and broadcasts them to connected browsers.
 *
 * This is the second Kafka consumer in the system:
 *   trading-aggregator  → builds OHLCV candles
 *   trading-market-api  → broadcasts to browser WebSocket clients
 *
 * Both consume the same trading.ticks topic independently
 * because they have DIFFERENT consumer group IDs:
 *   trading-aggregator  → group: "trading-aggregator"
 *   trading-market-api  → group: "trading-market-api"  ← this one
 *
 * Kafka delivers ALL messages to each group independently —
 * so both consumers see every tick without interfering with each other.
 *
 * ALSO WRITES TO REDIS:
 * Before broadcasting to WebSocket, we cache the latest tick in Redis.
 * This serves two purposes:
 * 1. QuoteController can serve the last known price even after
 *    the WebSocket connection drops
 * 2. On page load, the UI gets instant quotes without waiting
 *    for the next tick to arrive
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickBroadcastService {

    private final TickWebSocketHandler tickWebSocketHandler;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Receives each tick from Kafka and:
     * 1. Caches it in Redis (for QuoteController)
     * 2. Broadcasts it to subscribed WebSocket sessions (for React UI)
     *
     * groupId = "trading-market-api" — separate group from aggregator
     * so both consumers receive all ticks independently.
     *
     * containerFactory = "tickKafkaListenerContainerFactory" — wait,
     * this factory is defined in trading-aggregator's KafkaConsumerConfig.
     * Since both modules are on the same classpath, Spring finds it.
     * If this causes issues we'll define a separate factory here.
     */
    @KafkaListener(
        topics = KafkaTopics.TICKS,
        groupId = "trading-market-api",
        containerFactory = "tickKafkaListenerContainerFactory"
    )
    public void onTick(TickDto tick) {
        try {
            // Step 1: Cache in Redis with 1-hour TTL
            // TTL ensures stale data auto-expires if market closes
            // and no new ticks arrive for that instrument
            cacheInRedis(tick);

            // Step 2: Push to all subscribed browser sessions
            tickWebSocketHandler.broadcastTick(tick);

        } catch (Exception e) {
            log.error("Error broadcasting tick for token {}: {}",
                tick.getInstrumentToken(), e.getMessage());
        }
    }

    private void cacheInRedis(TickDto tick) {
        long token = tick.getInstrumentToken();

        // Cache full tick object — for QuoteController
        String tickKey = String.format(CacheKeys.TICK, token);
        redisTemplate.opsForValue().set(tickKey, tick, Duration.ofHours(1));

        // Cache just the LTP as a plain string — for fast LTP lookups
        if (tick.getLastPrice() != null) {
            String ltpKey = String.format(CacheKeys.LTP, token);
            redisTemplate.opsForValue().set(
                ltpKey, tick.getLastPrice().toPlainString(), Duration.ofHours(1)
            );
        }
    }
}