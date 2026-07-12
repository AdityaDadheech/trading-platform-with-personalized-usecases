package com.trading.api.controller;

import com.trading.aggregator.repository.OhlcvRepository;
import com.trading.api.dto.ApiResponse;
import com.trading.common.dto.OhlcvDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Serves OHLCV candlestick data to the React UI.
 *
 * The React chart (TradingView Lightweight Charts) calls these
 * endpoints on initial load and when the user scrolls back in time.
 *
 * Response format matches what Lightweight Charts expects:
 * [{ time: epoch_seconds, open, high, low, close, volume }, ...]
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ohlcv")
@RequiredArgsConstructor
public class OhlcvController {

    private final OhlcvRepository ohlcvRepository;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Get latest N candles for a symbol.
     * This is what the chart calls on first load.
     *
     * GET /api/v1/ohlcv/256265?interval=minute&limit=500
     *
     * Returns last 500 one-minute candles for NIFTY 50,
     * sorted oldest → newest.
     */
    @GetMapping("/{instrumentToken}")
    public ResponseEntity<ApiResponse<List<OhlcvDto>>> getLatestCandles(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "minute") String interval,
        @RequestParam(defaultValue = "500") int limit
    ) {
        log.debug("Fetching latest {} {} candles for token {}",
            limit, interval, instrumentToken);

        // Cap at 5000 to prevent accidental huge queries
        int cappedLimit = Math.min(limit, 5000);

        List<OhlcvDto> candles = ohlcvRepository.findLatestCandles(
            instrumentToken, interval, cappedLimit
        );

        return ResponseEntity.ok(ApiResponse.ok(candles));
    }

    /**
     * Get candles for a specific date range.
     * Called when user scrolls back in the chart.
     *
     * GET /api/v1/ohlcv/256265/range
     *     ?interval=minute&from=2026-06-01&to=2026-06-22
     */
    @GetMapping("/{instrumentToken}/range")
    public ResponseEntity<ApiResponse<List<OhlcvDto>>> getCandlesInRange(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "minute") String interval,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.debug("Fetching {} candles for token {} from {} to {}",
            interval, instrumentToken, from, to);

        Instant fromInstant = from.atStartOfDay(IST).toInstant();
        Instant toInstant   = to.atTime(23, 59, 59).atZone(IST).toInstant();

        List<OhlcvDto> candles = ohlcvRepository.findCandles(
            instrumentToken, interval, fromInstant, toInstant
        );

        return ResponseEntity.ok(ApiResponse.ok(candles));
    }

    /**
     * Get summary stats for a symbol — useful for the quote panel.
     *
     * GET /api/v1/ohlcv/256265/summary?interval=day
     *
     * Returns today's OHLCV from the daily candle.
     */
    @GetMapping("/{instrumentToken}/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "day") String interval
    ) {
        List<OhlcvDto> candles = ohlcvRepository.findLatestCandles(
            instrumentToken, interval, 1
        );

        if (candles.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("No data found for token " + instrumentToken));
        }

        OhlcvDto latest = candles.get(0);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "symbol",   latest.getTradingSymbol(),
            "exchange", latest.getExchange(),
            "date",     latest.getBucketTime().toString(),
            "open",     latest.getOpen(),
            "high",     latest.getHigh(),
            "low",      latest.getLow(),
            "close",    latest.getClose(),
            "volume",   latest.getVolume()
        )));
    }
}