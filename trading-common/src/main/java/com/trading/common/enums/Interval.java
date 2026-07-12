package com.trading.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Candlestick intervals supported by Kite Connect historical API.
 * The kiteValue must be passed as-is in API requests.
 */
@Getter
@RequiredArgsConstructor
public enum Interval {

    MINUTE_1("minute"),
    MINUTE_3("3minute"),
    MINUTE_5("5minute"),
    MINUTE_10("10minute"),
    MINUTE_15("15minute"),
    MINUTE_30("30minute"),
    MINUTE_60("60minute"),
    DAY("day");

    @JsonValue
    private final String kiteValue;

    public static Interval fromKiteValue(String value) {
        for (Interval i : values()) {
            if (i.kiteValue.equals(value)) return i;
        }
        throw new IllegalArgumentException("Unknown interval: " + value);
    }
}
