package com.trading.api.controller;

import com.trading.aggregator.repository.WatchlistRepository;
import com.trading.aggregator.service.HistoricalSyncService;
import com.trading.api.dto.ApiResponse;
import com.trading.common.dto.InstrumentDto;
import com.trading.common.enums.Interval;
import com.trading.kite.service.InstrumentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentRegistry instrumentRegistry;
    private final WatchlistRepository watchlistRepository;
    private final HistoricalSyncService historicalSyncService;

    /**
     * Search instruments by symbol or name.
     * GET /api/v1/instruments/search?q=RELIANCE&exchange=NSE
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<InstrumentDto>>> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "NSE") String exchange,
        @RequestParam(defaultValue = "20") int limit
    ) {
        List<InstrumentDto> results = instrumentRegistry.search(q, exchange, limit);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * Add to watchlist AND auto-sync historical data.
     * POST /api/v1/instruments/watchlist/{token}
     */
    @PostMapping("/watchlist/{instrumentToken}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addToWatchlistWithSync(
        @PathVariable long instrumentToken,
        @RequestParam(defaultValue = "false") boolean syncHistory
    ) {
        String symbol = instrumentRegistry.getSymbol(instrumentToken);
        String exchange = instrumentRegistry.getExchange(instrumentToken);

        if (symbol == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Unknown token: " + instrumentToken));
        }

        // Add to watchlist
        watchlistRepository.add(instrumentToken, symbol, exchange);
        log.info("Added {} to watchlist", symbol);

        int candlesSynced = 0;
        if (syncHistory) {
            // Auto-sync last 5 days of 1m, 5m, 15m and 30 days of daily
            for (Interval interval : List.of(Interval.MINUTE_1, Interval.MINUTE_5,
                                              Interval.MINUTE_15, Interval.DAY)) {
                int days = interval == Interval.DAY ? 30 : 5;
                candlesSynced += historicalSyncService.syncLastNDays(
                    instrumentToken, interval, days
                );
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "symbol", symbol,
            "exchange", exchange,
            "message", symbol + " added to watchlist",
            "candlesSynced", candlesSynced
        )));
    }
}