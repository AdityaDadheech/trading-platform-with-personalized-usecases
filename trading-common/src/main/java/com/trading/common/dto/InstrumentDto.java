package com.trading.common.dto;

import com.trading.common.enums.Exchange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A tradable instrument on an Indian exchange.
 * Populated from Kite's daily instrument dump CSV.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentDto {

    private Long instrumentToken;     // Kite's unique numeric identifier
    private String tradingSymbol;     // e.g. "RELIANCE", "NIFTY24DECFUT"
    private String name;              // company name
    private Exchange exchange;
    private String segment;           // e.g. "NSE", "NFO-FUT", "NFO-OPT"
    private String instrumentType;    // EQ, FUT, CE, PE
    private BigDecimal tickSize;      // minimum price movement
    private Integer lotSize;          // 1 for equity, 25/50/75 for F&O
    private BigDecimal strikePrice;   // 0 for equity and futures
    private String expiry;            // null for equity, date string for F&O
}
