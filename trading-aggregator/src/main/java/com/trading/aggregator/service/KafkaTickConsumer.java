package com.trading.aggregator.service;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.dto.TickDto;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes ticks from Kafka and feeds them to CandleBuilderService.
 *
 * WHY THIS IS SEPARATE FROM CandleBuilderService:
 * ──────────────────────────────────────────────────────────────
 * Single responsibility — this class only knows about Kafka.
 * CandleBuilderService only knows about candle logic.
 * If we swap Kafka for RabbitMQ tomorrow, we only change this class.
 * ──────────────────────────────────────────────────────────────
 *
 * @KafkaListener explained:
 *   topics        = which Kafka topic to read from
 *   groupId       = consumer group name
 *                   Kafka tracks our read position (offset) per group.
 *                   If the aggregator restarts, it resumes from where
 *                   it left off — no ticks are skipped or re-processed.
 *   containerFactory = which deserializer config to use
 *
 * CONCURRENCY:
 * We build candles for multiple intervals simultaneously.
 * One tick feeds 1m, 5m, and 15m candles all at once.
 * This is cheap because CandleBuilderService is just in-memory map ops.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaTickConsumer {

    private final CandleBuilderService candleBuilderService;

    /**
     * Called for every tick message on the trading.ticks topic.
     *
     * We build candles for three intervals from every single tick:
     *   1m  → for intraday scalping / short-term analysis
     *   5m  → standard for swing entry confirmation
     *   15m → for higher timeframe trend context
     *
     * More intervals = more candles in DB but same Kafka consumer load.
     * The cost is just extra ConcurrentHashMap lookups per tick.
     */
    @KafkaListener(
        topics = KafkaTopics.TICKS,
        groupId = "trading-aggregator",
        containerFactory = "tickKafkaListenerContainerFactory"
    )
    public void consume(TickDto tick) {
        try {
            // Build candles for all three intervals from this one tick
            candleBuilderService.processTick(tick, Interval.MINUTE_1);
            candleBuilderService.processTick(tick, Interval.MINUTE_5);
            candleBuilderService.processTick(tick, Interval.MINUTE_15);
        } catch (Exception e) {
            log.error("Error processing tick for token {}: {}",
                tick.getInstrumentToken(), e.getMessage(), e);
        }
    }
}