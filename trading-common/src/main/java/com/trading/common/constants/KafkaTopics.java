package com.trading.common.constants;

/**
 * All Kafka topic names in one place.
 * Any module that produces or consumes a topic references this class —
 * no magic strings scattered across the codebase.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Raw normalized ticks from WebSocket — high volume, one message per tick */
    public static final String TICKS = "trading.ticks";

    /** Built OHLCV candles from the aggregator — one message per closed candle */
    public static final String OHLCV_CANDLES = "trading.ohlcv.candles";

    /** Instrument metadata sync events */
    public static final String INSTRUMENTS = "trading.instruments";
}
