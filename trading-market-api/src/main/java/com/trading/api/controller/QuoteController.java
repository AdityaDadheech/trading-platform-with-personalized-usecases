package com.trading.api.controller;

import com.trading.api.dto.ApiResponse;
import com.trading.api.service.QuoteService;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Serves live quote data from Redis.
 *
 * During market hours: returns latest tick data (~200ms fresh)
 * Outside market hours: returns empty/null (no active ticks)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/quote")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    /**
     * Get full latest tick for an instrument.
     *
     * GET /api/v1/quote/256265
     *
     * Returns the full TickDto if market is open and instrument
     * is subscribed, otherwise returns success:false.
     */
    @GetMapping("/{instrumentToken}")
    public ResponseEntity<ApiResponse<TickDto>> getQuote(
        @PathVariable long instrumentToken
    ) {
        Optional<TickDto> tick = quoteService.getLatestTick(instrumentToken);

        return tick.map(t -> ResponseEntity.ok(ApiResponse.ok(t)))
            .orElse(ResponseEntity.ok(
                ApiResponse.<TickDto>error("No live quote available for token "
                    + instrumentToken + ". Market may be closed.")
            ));
    }

    /**
     * Get just the last traded price — lightweight endpoint.
     *
     * GET /api/v1/quote/256265/ltp
     *
     * Used by the watchlist panel to show LTP next to each symbol.
     */
    @GetMapping("/{instrumentToken}/ltp")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLtp(
        @PathVariable long instrumentToken
    ) {
        Optional<String> ltp = quoteService.getLastPrice(instrumentToken);

        return ltp.map(price -> ResponseEntity.ok(ApiResponse.ok(
                Map.of("instrumentToken", String.valueOf(instrumentToken), "ltp", price)
            )))
            .orElse(ResponseEntity.ok(
                ApiResponse.<Map<String, String>>error("No LTP available")
            ));
    }
}