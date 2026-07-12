package com.trading.aggregator.scheduler;

import com.trading.aggregator.service.CandleBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flushes the last open candles to DB at market close.
 *
 * WHY THIS IS NEEDED:
 * CandleBuilderService closes a candle only when the NEXT
 * tick arrives in a new time bucket. The last candle of the
 * day (15:29 candle) never gets a "next tick" to trigger its
 * close. Without this scheduler, the 15:29 candle would sit
 * in memory forever and never reach TimescaleDB.
 *
 * @Scheduled(cron = "0 32 15 * * MON-FRI") means:
 *   0        = second 0
 *   32       = minute 32
 *   15       = hour 15 (3 PM)
 *   *        = any day of month
 *   *        = any month
 *   MON-FRI  = weekdays only
 *
 * We flush at 15:32 (2 minutes after market close at 15:30)
 * to ensure all last ticks have been processed by Kafka consumer.
 *
 * Timezone = IST (Asia/Kolkata) — explicitly set so this works
 * regardless of the server's system timezone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCloseScheduler {

    private final CandleBuilderService candleBuilderService;

    @Scheduled(cron = "0 32 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void flushCandlesAtMarketClose() {
        log.info("Market close flush triggered — flushing all open candles...");
        candleBuilderService.flushAll();
    }
}