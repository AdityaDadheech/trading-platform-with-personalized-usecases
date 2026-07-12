package com.trading.aggregator.service;

import com.trading.aggregator.repository.OhlcvRepository;
import com.trading.common.dto.OhlcvDto;
import com.trading.common.enums.Interval;
import com.trading.kite.service.KiteHistoricalService;
import com.trading.kite.service.InstrumentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Orchestrates pulling historical OHLCV data from Kite REST API
 * and writing it to TimescaleDB.
 *
 * WHY THIS IS SEPARATE FROM CandleBuilderService:
 * CandleBuilderService builds candles from LIVE ticks in real time.
 * HistoricalSyncService pulls HISTORICAL candles from Kite's REST API.
 * Two different data sources, two different services.
 * Both write to the same OhlcvRepository — single writer to TimescaleDB.
 *
 * KITE API LIMITS (important):
 * - Minute data: max 60 days per request
 * - Day data: max 2000 candles (~8 years)
 * - Rate limit: ~3 requests/second — we use 500ms delay between calls
 *   to stay safely within limits
 *
 * The upsert in OhlcvRepository means this is safe to re-run —
 * existing candles get updated, no duplicates created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalSyncService {

    private final KiteHistoricalService kiteHistoricalService;
    private final OhlcvRepository ohlcvRepository;
    private final InstrumentRegistry instrumentRegistry;

    // NSE market hours
    private static final LocalTime MARKET_OPEN  = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    /**
     * Syncs historical OHLCV data for a single instrument.
     *
     * @param instrumentToken  Kite's numeric token (e.g. 256265 for NIFTY 50)
     * @param interval         candle timeframe
     * @param fromDate         start date (inclusive)
     * @param toDate           end date (inclusive)
     * @return number of candles saved
     */
    public int sync(
        long instrumentToken,
        Interval interval,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        // Resolve symbol from registry
        String symbol = instrumentRegistry.getSymbol(instrumentToken);
        String exchange = instrumentRegistry.getExchange(instrumentToken);

        if (symbol == null) {
            throw new IllegalArgumentException(
                "Instrument token " + instrumentToken + " not found in registry. " +
                "Ensure InstrumentRegistry is loaded — call /api/v1/auth/login first."
            );
        }

        log.info("Starting historical sync for {} ({}) | interval={} | {} → {}",
            symbol, instrumentToken, interval.getKiteValue(), fromDate, toDate);

        // Build from/to as LocalDateTime with market hours
        // For minute data: from = 09:15, to = 15:30
        // For day data: from = 00:00, to = 23:59
        LocalDateTime from = LocalDateTime.of(fromDate,
            interval == Interval.DAY ? LocalTime.MIDNIGHT : MARKET_OPEN);
        LocalDateTime to = LocalDateTime.of(toDate,
            interval == Interval.DAY ? LocalTime.of(23, 59) : MARKET_CLOSE);

        // Fetch from Kite REST API
        List<OhlcvDto> candles = kiteHistoricalService.fetchHistoricalEquity(
            instrumentToken, symbol, exchange, interval, from, to
        );

        if (candles.isEmpty()) {
            log.warn("No candles returned for {} | {} → {}", symbol, fromDate, toDate);
            return 0;
        }

        // Batch upsert to TimescaleDB
        ohlcvRepository.saveAll(candles);

        log.info("Sync complete for {} — {} candles saved to TimescaleDB", symbol, candles.size());
        return candles.size();
    }

    /**
     * Syncs the last N days of data for a given instrument and interval.
     * Convenience method for the most common use case.
     *
     * @param instrumentToken  Kite's numeric token
     * @param interval         candle timeframe
     * @param days             how many trading days back to sync
     * @return number of candles saved
     */
    public int syncLastNDays(long instrumentToken, Interval interval, int days) {
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days);
        return sync(instrumentToken, interval, from, to);
    }

    /**
     * Syncs multiple instruments in sequence.
     * Used for bulk backfill of a watchlist.
     *
     * 500ms delay between requests to respect Kite's rate limit.
     *
     * @param tokens    list of instrument tokens
     * @param interval  candle timeframe
     * @param days      how many days back
     * @return total candles saved across all instruments
     */
    public int syncMultiple(List<Long> tokens, Interval interval, int days) {
        int total = 0;

        for (Long token : tokens) {
            try {
                total += syncLastNDays(token, interval, days);

                // Rate limit: Kite allows ~3 req/sec
                // 500ms between requests = 2 req/sec = safely within limit
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sync interrupted after {} candles", total);
                break;
            } catch (Exception e) {
                log.error("Failed to sync token {}: {}", token, e.getMessage());
                // Continue with next instrument — don't let one failure stop the batch
            }
        }

        log.info("Bulk sync complete — {} total candles saved for {} instruments",
            total, tokens.size());
        return total;
    }
}