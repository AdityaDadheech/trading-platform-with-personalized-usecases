package com.trading.common.dto;

import com.trading.common.enums.Interval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OHLCV candlestick bar.
 * Used for both historical data fetched from Kite REST
 * and live candles built by the aggregator from ticks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OhlcvDto {

    private Long instrumentToken;
    private String tradingSymbol;
    private String exchange;
    private Interval interval;

    private Instant bucketTime;       // candle open time (start of the bar)
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
    private Long openInterest;        // 0 for equity, non-zero for F&O
}
