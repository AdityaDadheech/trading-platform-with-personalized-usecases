package com.trading.aggregator.service;

import com.trading.aggregator.repository.OhlcvRepository;
import com.trading.common.dto.OhlcvDto;
import com.trading.common.dto.TickDto;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds OHLCV candles in memory from a stream of ticks.
 *
 * HOW IT WORKS:
 * ──────────────────────────────────────────────────────────────
 * Maintains one "open candle" per instrument per interval in memory.
 * When a tick arrives:
 *   1. Calculate which bucket (minute) this tick belongs to
 *   2. If same bucket as current candle → update H/L/C/V
 *   3. If new bucket → close current candle (write to DB) → open new one
 *
 * EXAMPLE for 1-minute candles on NIFTY:
 *   09:15:01 tick 22450 → open new candle  O=22450 H=22450 L=22450 C=22450
 *   09:15:15 tick 22460 → update           O=22450 H=22460 L=22450 C=22460
 *   09:15:45 tick 22440 → update           O=22450 H=22460 L=22440 C=22440
 *   09:16:01 tick 22455 → CLOSE candle     write to DB
 *                       → open new candle  O=22455 ...
 * ──────────────────────────────────────────────────────────────
 *
 * THREAD SAFETY:
 * Kafka consumer calls processTick() on its own thread.
 * ConcurrentHashMap handles concurrent access safely.
 * Each instrument's candle is only written by one partition's
 * consumer thread (same key → same partition → same thread).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleBuilderService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OhlcvRepository ohlcvRepository;

    /**
     * In-memory store of currently open candles.
     * Key: instrumentToken + "_" + interval  (e.g. "256265_minute")
     * Value: the candle currently being built
     */
    private final Map<String, OhlcvDto> openCandles = new ConcurrentHashMap<>();

    /**
     * Process a single tick — the core method.
     * Called by KafkaTickConsumer for every message on trading.ticks topic.
     *
     * @param tick     normalized tick from Kafka
     * @param interval which candle timeframe to build (1m, 5m, 15m, etc.)
     */
    public void processTick(TickDto tick, Interval interval) {
        if (tick.getLastPrice() == null || tick.getLastPrice().compareTo(BigDecimal.ZERO) == 0) {
            return; // skip zero-price ticks (pre-market, circuit breaker, etc.)
        }

        String key = tick.getInstrumentToken() + "_" + interval.getKiteValue();
        Instant bucketTime = getBucketTime(tick.getTickTimestamp(), interval);

        OhlcvDto existing = openCandles.get(key);

        if (existing == null) {
            // No open candle yet — start a fresh one
            openCandles.put(key, openNewCandle(tick, interval, bucketTime));

        } else if (existing.getBucketTime().equals(bucketTime)) {
            // Same time bucket — update the open candle
            updateCandle(existing, tick);

        } else {
            // New time bucket — close the old candle and start fresh
            closeCandle(existing);
            openCandles.put(key, openNewCandle(tick, interval, bucketTime));
        }
    }

    /**
     * Force-close all open candles.
     * Called at market close (15:30 IST) to flush the last candles to DB.
     * Otherwise the last candle of the day would never be written.
     */
    public void flushAll() {
        log.info("Flushing {} open candles to DB...", openCandles.size());
        openCandles.values().forEach(this::closeCandle);
        openCandles.clear();
        log.info("All candles flushed.");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Calculate which time bucket a tick belongs to.
     *
     * For 1-minute candles: truncate to the minute
     *   09:15:34 IST → 09:15:00 IST
     *
     * For 5-minute candles: floor to nearest 5-minute mark
     *   09:17:45 IST → 09:15:00 IST
     *   09:21:12 IST → 09:20:00 IST
     *
     * All buckets use IST because NSE operates in IST.
     */
    private Instant getBucketTime(Instant tickTime, Interval interval) {
        ZonedDateTime zdt = tickTime.atZone(IST);

        return switch (interval) {
            case MINUTE_1  -> zdt.truncatedTo(ChronoUnit.MINUTES).toInstant();
            case MINUTE_3  -> floorToMinutes(zdt, 3);
            case MINUTE_5  -> floorToMinutes(zdt, 5);
            case MINUTE_10 -> floorToMinutes(zdt, 10);
            case MINUTE_15 -> floorToMinutes(zdt, 15);
            case MINUTE_30 -> floorToMinutes(zdt, 30);
            case MINUTE_60 -> floorToMinutes(zdt, 60);
            case DAY       -> zdt.truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    private Instant floorToMinutes(ZonedDateTime zdt, int minutes) {
        int minute = zdt.getMinute();
        int flooredMinute = (minute / minutes) * minutes;
        return zdt.withMinute(flooredMinute)
                  .withSecond(0)
                  .withNano(0)
                  .toInstant();
    }

    /**
     * Creates a new candle with this tick as the first data point.
     * Open = Close = lastPrice (we only have one tick so far).
     */
    private OhlcvDto openNewCandle(TickDto tick, Interval interval, Instant bucketTime) {
        return OhlcvDto.builder()
            .instrumentToken(tick.getInstrumentToken())
            .tradingSymbol(tick.getTradingSymbol())
            .exchange(tick.getExchange())
            .interval(interval)
            .bucketTime(bucketTime)
            .open(tick.getLastPrice())
            .high(tick.getLastPrice())
            .low(tick.getLastPrice())
            .close(tick.getLastPrice())
            .volume(tick.getVolumeTraded() != null ? tick.getVolumeTraded() : 0L)
            .openInterest(tick.getOpenInterest() != null ? tick.getOpenInterest() : 0L)
            .build();
    }

    /**
     * Updates an existing open candle with new tick data.
     *
     * High/Low use compareTo() because BigDecimal.max/min are not null-safe.
     * Volume uses the tick's cumulative volume (Kite sends total volume
     * for the day, not incremental — so we just take the latest value).
     */
    private void updateCandle(OhlcvDto candle, TickDto tick) {
        BigDecimal price = tick.getLastPrice();

        if (price.compareTo(candle.getHigh()) > 0) candle.setHigh(price);
        if (price.compareTo(candle.getLow())  < 0) candle.setLow(price);
        candle.setClose(price);

        if (tick.getVolumeTraded() != null) {
            candle.setVolume(tick.getVolumeTraded());
        }
        if (tick.getOpenInterest() != null) {
            candle.setOpenInterest(tick.getOpenInterest());
        }
    }

    /**
     * Closes a candle by writing it to TimescaleDB.
     * After this call the candle is persisted and removed from memory.
     */
    private void closeCandle(OhlcvDto candle) {
        try {
            ohlcvRepository.save(candle);
            log.debug("Candle closed → {} {} {} O:{} H:{} L:{} C:{} V:{}",
                candle.getTradingSymbol(),
                candle.getInterval().getKiteValue(),
                candle.getBucketTime(),
                candle.getOpen(), candle.getHigh(),
                candle.getLow(),  candle.getClose(),
                candle.getVolume());
        } catch (Exception e) {
            log.error("Failed to persist candle for {} {}: {}",
                candle.getTradingSymbol(),
                candle.getInterval().getKiteValue(),
                e.getMessage());
        }
    }
}