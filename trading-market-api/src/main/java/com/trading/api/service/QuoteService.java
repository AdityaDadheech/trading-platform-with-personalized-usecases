package com.trading.api.service;

import com.trading.common.constants.CacheKeys;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads live tick data from Redis.
 *
 * WHY REDIS FOR QUOTES:
 * The React UI's quote panel needs the latest price for each
 * watchlist symbol — updated every 200ms during market hours.
 * Reading from TimescaleDB for every quote request would be
 * too slow and wasteful. Redis gives us sub-millisecond reads.
 *
 * The TickIngestionListener (Phase 2 enhancement) will write
 * each tick to Redis after publishing to Kafka. For now, we
 * read from Redis and return null if not present (market closed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Gets the latest tick for an instrument from Redis.
     * Returns empty if market is closed or instrument not subscribed.
     */
    public Optional<TickDto> getLatestTick(long instrumentToken) {
        String key = String.format(CacheKeys.TICK, instrumentToken);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof TickDto tick) {
                return Optional.of(tick);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Redis error fetching tick for token {}: {}",
                instrumentToken, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gets just the last traded price for an instrument.
     * Fastest possible quote — just one Redis key lookup.
     */
    public Optional<String> getLastPrice(long instrumentToken) {
        String key = String.format(CacheKeys.LTP, instrumentToken);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Optional.of(value.toString()) : Optional.empty();
        } catch (Exception e) {
            log.error("Redis error fetching LTP for token {}: {}",
                instrumentToken, e.getMessage());
            return Optional.empty();
        }
    }
}