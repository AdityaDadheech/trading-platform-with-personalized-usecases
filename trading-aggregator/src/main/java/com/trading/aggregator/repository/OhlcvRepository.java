package com.trading.aggregator.repository;

import com.trading.common.dto.OhlcvDto;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Writes OHLCV candles to TimescaleDB.
 *
 * WHY JdbcTemplate INSTEAD OF JPA:
 * ──────────────────────────────────────────────────────────────
 * JPA/Hibernate works well for standard CRUD but TimescaleDB's
 * hypertable requires specific SQL for upserts (INSERT ON CONFLICT).
 * JdbcTemplate gives us direct SQL control without Hibernate
 * fighting us on the hypertable structure.
 *
 * The ON CONFLICT DO UPDATE (upsert) is important because:
 * If the aggregator restarts mid-candle, it will re-process some
 * ticks and try to insert a candle that already exists.
 * Upsert handles this gracefully — updates instead of failing.
 * ──────────────────────────────────────────────────────────────
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OhlcvRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Upserts a single OHLCV candle into TimescaleDB.
     *
     * INSERT ... ON CONFLICT DO UPDATE means:
     * - If no candle exists for this token+interval+bucket → INSERT
     * - If one already exists → UPDATE with new values
     *
     * This makes the aggregator idempotent — safe to re-run.
     */
    public void save(OhlcvDto candle) {
        String sql = """
            INSERT INTO ohlcv_candles
                (instrument_token, trading_symbol, exchange, interval,
                 bucket_time, open, high, low, close, volume, open_interest)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_token, interval, bucket_time)
            DO UPDATE SET
                open           = EXCLUDED.open,
                high           = EXCLUDED.high,
                low            = EXCLUDED.low,
                close          = EXCLUDED.close,
                volume         = EXCLUDED.volume,
                open_interest  = EXCLUDED.open_interest
            """;

        jdbcTemplate.update(sql,
            candle.getInstrumentToken(),
            candle.getTradingSymbol(),
            candle.getExchange(),
            candle.getInterval().getKiteValue(),
            Timestamp.from(candle.getBucketTime()),
            candle.getOpen(),
            candle.getHigh(),
            candle.getLow(),
            candle.getClose(),
            candle.getVolume(),
            candle.getOpenInterest()
        );
    }

    /**
     * Batch upsert for historical sync — much faster than individual saves.
     * Uses Spring's batchUpdate which sends all rows in one DB round trip.
     */
    public void saveAll(List<OhlcvDto> candles) {
        if (candles.isEmpty()) return;

        String sql = """
            INSERT INTO ohlcv_candles
                (instrument_token, trading_symbol, exchange, interval,
                 bucket_time, open, high, low, close, volume, open_interest)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_token, interval, bucket_time)
            DO UPDATE SET
                open           = EXCLUDED.open,
                high           = EXCLUDED.high,
                low            = EXCLUDED.low,
                close          = EXCLUDED.close,
                volume         = EXCLUDED.volume,
                open_interest  = EXCLUDED.open_interest
            """;

        jdbcTemplate.batchUpdate(sql, candles, candles.size(), (ps, candle) -> {
            ps.setLong(1, candle.getInstrumentToken());
            ps.setString(2, candle.getTradingSymbol());
            ps.setString(3, candle.getExchange());
            ps.setString(4, candle.getInterval().getKiteValue());
            ps.setTimestamp(5, Timestamp.from(candle.getBucketTime()));
            ps.setBigDecimal(6, candle.getOpen());
            ps.setBigDecimal(7, candle.getHigh());
            ps.setBigDecimal(8, candle.getLow());
            ps.setBigDecimal(9, candle.getClose());
            ps.setLong(10, candle.getVolume());
            ps.setLong(11, candle.getOpenInterest() != null ? candle.getOpenInterest() : 0L);
        });

        log.debug("Batch saved {} candles to TimescaleDB", candles.size());
    }

    /**
     * Fetches OHLCV candles for a given instrument, interval and time range.
     * Results sorted oldest → newest (required by charting libraries).
     */
    public List<OhlcvDto> findCandles(
            long instrumentToken,
            String interval,
            Instant from,
            Instant to
    ) {
        String sql = """
        SELECT instrument_token, trading_symbol, exchange, interval,
               bucket_time, open, high, low, close, volume, open_interest
        FROM ohlcv_candles
        WHERE instrument_token = ?
          AND interval = ?
          AND bucket_time BETWEEN ? AND ?
        ORDER BY bucket_time ASC
        """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> OhlcvDto.builder()
                        .instrumentToken(rs.getLong("instrument_token"))
                        .tradingSymbol(rs.getString("trading_symbol"))
                        .exchange(rs.getString("exchange"))
                        .interval(Interval.fromKiteValue(rs.getString("interval")))
                        .bucketTime(rs.getTimestamp("bucket_time").toInstant())
                        .open(rs.getBigDecimal("open"))
                        .high(rs.getBigDecimal("high"))
                        .low(rs.getBigDecimal("low"))
                        .close(rs.getBigDecimal("close"))
                        .volume(rs.getLong("volume"))
                        .openInterest(rs.getLong("open_interest"))
                        .build(),
                instrumentToken,
                interval,
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    /**
     * Fetches the latest N candles for a given instrument and interval.
     * Used by the chart UI on initial load.
     */
    public List<OhlcvDto> findLatestCandles(
            long instrumentToken,
            String interval,
            int limit
    ) {
        // Use TimescaleDB's time_bucket_gapfill or just direct DESC query
        // without the outer re-sort — chart will reverse in Java which is O(n) not O(n log n)
        String sql = """
        SELECT instrument_token, trading_symbol, exchange, interval,
               bucket_time, open, high, low, close, volume, open_interest
        FROM ohlcv_candles
        WHERE instrument_token = ?
          AND interval = ?
        ORDER BY bucket_time DESC
        LIMIT ?
        """;

        List<OhlcvDto> candles = jdbcTemplate.query(sql,
                (rs, rowNum) -> OhlcvDto.builder()
                        .instrumentToken(rs.getLong("instrument_token"))
                        .tradingSymbol(rs.getString("trading_symbol"))
                        .exchange(rs.getString("exchange"))
                        .interval(Interval.fromKiteValue(rs.getString("interval")))
                        .bucketTime(rs.getTimestamp("bucket_time").toInstant())
                        .open(rs.getBigDecimal("open"))
                        .high(rs.getBigDecimal("high"))
                        .low(rs.getBigDecimal("low"))
                        .close(rs.getBigDecimal("close"))
                        .volume(rs.getLong("volume"))
                        .openInterest(rs.getLong("open_interest"))
                        .build(),
                instrumentToken,
                interval,
                limit
        );

        // Reverse in Java — O(n) in memory, much faster than DB sort
        java.util.Collections.reverse(candles);
        return candles;
    }
}