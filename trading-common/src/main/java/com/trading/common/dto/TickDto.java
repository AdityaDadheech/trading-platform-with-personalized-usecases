package com.trading.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized tick DTO - our internal representation.
 * Decoupled from Kite SDK's Tick object so the rest of the system
 * never depends on the Kite SDK directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickDto {

    private Long instrumentToken;
    private String tradingSymbol;     // e.g. "RELIANCE", "NIFTY24DECFUT"
    private String exchange;          // e.g. "NSE", "NFO"

    private BigDecimal lastPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;    // previous day close

    private Long volumeTraded;
    private Long totalBuyQuantity;
    private Long totalSellQuantity;

    private BigDecimal averageTradePrice;

    // Best bid/ask (top of book)
    private BigDecimal bestBidPrice;
    private Integer bestBidQuantity;
    private BigDecimal bestAskPrice;
    private Integer bestAskQuantity;

    // OI for F&O instruments
    private Long openInterest;
    private Long openInterestDayHigh;
    private Long openInterestDayLow;

    private Instant tickTimestamp;    // exchange timestamp
    private Instant receivedAt;       // when we received it
}
