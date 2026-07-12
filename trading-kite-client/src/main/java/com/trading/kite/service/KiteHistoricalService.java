package com.trading.kite.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.trading.common.dto.OhlcvDto;
import com.trading.common.enums.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Fetches historical OHLCV data from Kite Connect REST API.
 *
 * KITE API LIMITS (important for scheduler design):
 * ─────────────────────────────────────────────────
 *  minute data  → max 60 days per request
 *  day data     → max 2000 candles per request (~8 years)
 *  Rate limit   → ~3 requests/second (be conservative, use 1 req/sec)
 *
 * MARKET HOURS (IST):
 *  Pre-open     09:00 – 09:15
 *  Market open  09:15 – 15:30
 *  After close  15:30 onwards
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiteHistoricalService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter KITE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KiteConnect kiteConnect;

    /**
     * Fetches historical OHLCV candles for a given instrument and time range.
     *
     * @param instrumentToken  Kite's numeric token (e.g. 256265 for NIFTY 50)
     * @param tradingSymbol    human-readable symbol for logging (e.g. "NIFTY 50")
     * @param exchange         e.g. "NSE"
     * @param interval         candle timeframe (MINUTE_1, MINUTE_5, DAY, etc.)
     * @param from             range start (IST)
     * @param to               range end (IST)
     * @param continuous       true for continuous F&O contracts (ignore for equity)
     * @param oi               true to include open interest (for F&O)
     * @return list of OhlcvDto candles sorted oldest→newest
     */
    public List<OhlcvDto> fetchHistorical(
        long instrumentToken,
        String tradingSymbol,
        String exchange,
        Interval interval,
        LocalDateTime from,
        LocalDateTime to,
        boolean continuous,
        boolean oi
    ) {
        log.info("Fetching {} candles for {} | {} → {}",
            interval.getKiteValue(), tradingSymbol, from, to);

        try {
            Date fromDate = toDate(from);
            Date toDate = toDate(to);

            HistoricalData historicalData = kiteConnect.getHistoricalData(
                fromDate,
                toDate,
                String.valueOf(instrumentToken),
                interval.getKiteValue(),
                continuous,
                oi
            );

            List<OhlcvDto> candles = mapToDto(
                historicalData, instrumentToken, tradingSymbol, exchange, interval
            );

            log.info("Fetched {} candles for {} [{}]",
                candles.size(), tradingSymbol, interval.getKiteValue());

            return candles;

        } catch (KiteException e) {
            log.error("Kite API error fetching historical data for {}: [{}] {}",
                tradingSymbol, e.code, e.message);
            throw new RuntimeException("Kite historical fetch failed: " + e.message, e);
        } catch (IOException e) {
            log.error("IO error fetching historical data for {}: {}", tradingSymbol, e.getMessage());
            throw new RuntimeException("Kite historical fetch IO error", e);
        }
    }

    /**
     * Convenience overload for equity instruments (continuous=false, oi=false).
     */
    public List<OhlcvDto> fetchHistoricalEquity(
        long instrumentToken,
        String tradingSymbol,
        String exchange,
        Interval interval,
        LocalDateTime from,
        LocalDateTime to
    ) {
        return fetchHistorical(
            instrumentToken, tradingSymbol, exchange, interval, from, to, false, false
        );
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private List<OhlcvDto> mapToDto(
        HistoricalData historicalData,
        long instrumentToken,
        String tradingSymbol,
        String exchange,
        Interval interval
    ) {
        List<OhlcvDto> result = new ArrayList<>();

        if (historicalData == null || historicalData.dataArrayList == null) {
            return result;
        }

        for (HistoricalData candle : historicalData.dataArrayList) {
            result.add(OhlcvDto.builder()
                .instrumentToken(instrumentToken)
                .tradingSymbol(tradingSymbol)
                .exchange(exchange)
                .interval(interval)
                .bucketTime(parseTimestamp(candle.timeStamp))
                .open(toBigDecimal(candle.open))
                .high(toBigDecimal(candle.high))
                .low(toBigDecimal(candle.low))
                .close(toBigDecimal(candle.close))
                .volume((long) candle.volume)
                .openInterest((long) candle.oi)
                .build());
        }

        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(IST).toInstant());
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return Instant.EPOCH;
        try {
            // Kite returns "+0530" (no colon) — use ISO_OFFSET_DATE_TIME
            // which handles both "+05:30" and "+0530" formats
            return ZonedDateTime.parse(timestamp,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
                    .toInstant();
        } catch (Exception e) {
            log.warn("Could not parse timestamp '{}': {}", timestamp, e.getMessage());
            return Instant.EPOCH;
        }
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
