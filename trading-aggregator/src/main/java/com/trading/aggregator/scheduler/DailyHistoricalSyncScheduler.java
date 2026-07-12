package com.trading.aggregator.scheduler;

import com.trading.aggregator.service.HistoricalSyncService;
import com.trading.aggregator.repository.WatchlistRepository;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Runs every morning at 8:30 AM IST on weekdays.
 * Pulls yesterday's OHLCV data for all watchlist instruments
 * so charts are always up to date without manual API calls.
 *
 * Runs BEFORE market open (9:15 AM) so data is ready when you sit down.
 * Fetches last 5 days to catch any missed days (weekends, holidays).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyHistoricalSyncScheduler {

    private final HistoricalSyncService historicalSyncService;
    private final WatchlistRepository watchlistRepository;

    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void syncDailyData() {
        log.info("=== Daily historical sync starting (8:30 AM IST) ===");

        List<Map<String, Object>> watchlist = watchlistRepository.findAll();

        if (watchlist.isEmpty()) {
            log.warn("Watchlist is empty — nothing to sync");
            return;
        }

        List<Long> tokens = watchlist.stream()
            .map(item -> ((Number) item.get("instrument_token")).longValue())
            .toList();

        log.info("Syncing {} instruments: {}", tokens.size(),
            watchlist.stream().map(i -> (String) i.get("trading_symbol")).toList());

        // Sync last 5 days for all intervals we care about
        for (Interval interval : List.of(Interval.MINUTE_1, Interval.MINUTE_5,
                                          Interval.MINUTE_15, Interval.DAY)) {
            try {
                int days = interval == Interval.DAY ? 30 : 5;
                int total = historicalSyncService.syncMultiple(tokens, interval, days);
                log.info("Synced {} {} candles across {} instruments",
                    total, interval.getKiteValue(), tokens.size());
            } catch (Exception e) {
                log.error("Failed to sync {} data: {}", interval.getKiteValue(), e.getMessage());
            }
        }

        log.info("=== Daily historical sync complete ===");
    }
}