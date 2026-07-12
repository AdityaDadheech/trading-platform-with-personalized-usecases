package com.trading.api.controller;

import com.trading.aggregator.service.HistoricalSyncService;
import com.trading.api.dto.ApiResponse;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for triggering historical OHLCV data sync.
 *
 * These are admin/utility endpoints — not used by the React UI directly.
 * Use them to backfill data before market open or for initial setup.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/historical")
@RequiredArgsConstructor
public class HistoricalSyncController {

    private final HistoricalSyncService historicalSyncService;

    /**
     * Sync last N days for a single instrument.
     *
     * Example:
     * POST /api/v1/historical/sync/256265?interval=minute&days=5
     *
     * This pulls the last 5 days of 1-minute candles for NIFTY 50
     * and writes them to TimescaleDB.
     *
     * Common instrument tokens:
     *   256265  = NIFTY 50
     *   260105  = NIFTY BANK
     *   408065  = RELIANCE
     *   738561  = INFY
     *   341249  = TCS
     */
    @PostMapping("/sync/{instrumentToken}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncInstrument(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "minute") String interval,
        @RequestParam(defaultValue = "5") int days
    ) {
        log.info("Manual sync triggered for token={} interval={} days={}",
            instrumentToken, interval, days);

        Interval iv = Interval.fromKiteValue(interval);
        int count = historicalSyncService.syncLastNDays(instrumentToken, iv, days);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "instrumentToken", instrumentToken,
            "interval", interval,
            "days", days,
            "candlesSaved", count
        )));
    }

    /**
     * Sync a specific date range for a single instrument.
     *
     * Example:
     * POST /api/v1/historical/sync/256265/range
     *      ?interval=day&from=2024-01-01&to=2024-06-01
     */
    @PostMapping("/sync/{instrumentToken}/range")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncRange(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "day") String interval,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("Range sync triggered for token={} interval={} from={} to={}",
            instrumentToken, interval, from, to);

        Interval iv = Interval.fromKiteValue(interval);
        int count = historicalSyncService.sync(instrumentToken, iv, from, to);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "instrumentToken", instrumentToken,
            "interval", interval,
            "from", from.toString(),
            "to", to.toString(),
            "candlesSaved", count
        )));
    }

    /**
     * Sync multiple instruments at once — useful for backfilling a watchlist.
     *
     * Example:
     * POST /api/v1/historical/sync/bulk?interval=minute&days=5
     * Body: [256265, 408065, 738561]
     */
    @PostMapping("/sync/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncBulk(
        @RequestBody List<Long> tokens,
        @RequestParam(defaultValue = "minute") String interval,
        @RequestParam(defaultValue = "5") int days
    ) {
        log.info("Bulk sync triggered for {} instruments interval={} days={}",
            tokens.size(), interval, days);

        Interval iv = Interval.fromKiteValue(interval);
        int total = historicalSyncService.syncMultiple(tokens, iv, days);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "instruments", tokens.size(),
            "interval", interval,
            "days", days,
            "totalCandlesSaved", total
        )));
    }
}