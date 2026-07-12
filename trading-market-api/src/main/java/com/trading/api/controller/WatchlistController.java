package com.trading.api.controller;

import com.trading.aggregator.repository.WatchlistRepository;
import com.trading.api.dto.ApiResponse;
import com.trading.kite.service.InstrumentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD endpoints for the user's watchlist.
 *
 * The watchlist is stored in TimescaleDB (watchlist table)
 * and seeded with 5 default instruments via init-db.sql.
 *
 * The React UI uses this to populate the left panel of the dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistRepository watchlistRepository;
    private final InstrumentRegistry instrumentRegistry;

    /**
     * Get all watchlist items.
     *
     * GET /api/v1/watchlist
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWatchlist() {
        return ResponseEntity.ok(ApiResponse.ok(watchlistRepository.findAll()));
    }

    /**
     * Add an instrument to the watchlist.
     *
     * POST /api/v1/watchlist/256265
     */
    @PostMapping("/{instrumentToken}")
    public ResponseEntity<ApiResponse<Map<String, String>>> addToWatchlist(
        @PathVariable long instrumentToken
    ) {
        String symbol = instrumentRegistry.getSymbol(instrumentToken);
        String exchange = instrumentRegistry.getExchange(instrumentToken);

        if (symbol == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Unknown instrument token: " + instrumentToken));
        }

        watchlistRepository.add(instrumentToken, symbol, exchange);
        log.info("Added {} ({}) to watchlist", symbol, instrumentToken);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "message", symbol + " added to watchlist",
            "symbol", symbol,
            "exchange", exchange
        )));
    }

    /**
     * Remove an instrument from the watchlist.
     *
     * DELETE /api/v1/watchlist/256265
     */
    @DeleteMapping("/{instrumentToken}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeFromWatchlist(
        @PathVariable long instrumentToken
    ) {
        if (!watchlistRepository.exists(instrumentToken)) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Token " + instrumentToken + " not in watchlist"));
        }

        watchlistRepository.remove(instrumentToken);
        log.info("Removed token {} from watchlist", instrumentToken);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "message", "Removed from watchlist"
        )));
    }
}