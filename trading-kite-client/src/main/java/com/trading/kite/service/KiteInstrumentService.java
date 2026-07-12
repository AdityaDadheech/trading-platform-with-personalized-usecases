package com.trading.kite.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Instrument;
import com.trading.common.dto.InstrumentDto;
import com.trading.common.enums.Exchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches the full instrument list from Kite Connect.
 *
 * Kite publishes a fresh CSV dump of all tradable instruments every day
 * before market open (~8:00 AM IST). This service downloads and parses it.
 *
 * The instrument list contains ~200,000 rows across all exchanges.
 * We filter to the exchanges we care about (NSE equity + NFO F&O for Phase 1).
 *
 * The instrument_token field is what you use for:
 *   - WebSocket subscriptions
 *   - Historical data requests
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiteInstrumentService {

    private final KiteConnect kiteConnect;

    /**
     * Downloads all instruments from Kite and returns them as InstrumentDto list.
     * Filters to NSE and NFO by default (equity + F&O).
     *
     * Call this once at startup and once daily at ~8:00 AM IST.
     * Result should be stored in DB (instruments table) for fast lookups.
     *
     * @return list of instruments across NSE + NFO
     */
    public List<InstrumentDto> fetchAllInstruments() {
        log.info("Fetching instrument list from Kite Connect...");

        try {
            // Kite SDK fetches instruments for a given exchange, or all if null
            List<Instrument> nseInstruments = kiteConnect.getInstruments("NSE");
            List<Instrument> nfoInstruments = kiteConnect.getInstruments("NFO");
            List<Instrument> bseInstruments = kiteConnect.getInstruments("BSE");

            List<InstrumentDto> result = new ArrayList<>();
            result.addAll(mapToDto(nseInstruments, Exchange.NSE));
            result.addAll(mapToDto(nfoInstruments, Exchange.NFO));
            result.addAll(mapToDto(bseInstruments, Exchange.BSE));

            log.info("Fetched {} NSE + {} NFO instruments ({} total)",
                nseInstruments.size(), nfoInstruments.size(), result.size());

            return result;

        } catch (KiteException e) {
            log.error("Kite API error fetching instruments: [{}] {}", e.code, e.message);
            throw new RuntimeException("Failed to fetch instruments from Kite", e);
        } catch (IOException e) {
            log.error("IO error fetching instruments: {}", e.getMessage());
            throw new RuntimeException("IO error fetching instruments", e);
        }
    }

    /**
     * Fetches instruments for a specific exchange only.
     *
     * @param exchange  e.g. "NSE", "NFO", "BSE", "MCX"
     */
    public List<InstrumentDto> fetchInstrumentsByExchange(String exchange) {
        log.info("Fetching instruments for exchange: {}", exchange);
        try {
            List<Instrument> instruments = kiteConnect.getInstruments(exchange);
            Exchange ex = Exchange.valueOf(exchange);
            List<InstrumentDto> result = mapToDto(instruments, ex);
            log.info("Fetched {} instruments for {}", result.size(), exchange);
            return result;
        } catch (KiteException e) {
            throw new RuntimeException("Kite instruments fetch failed for " + exchange, e);
        } catch (IOException e) {
            throw new RuntimeException("IO error fetching instruments for " + exchange, e);
        }
    }

    /**
     * Builds a token → InstrumentDto lookup map for fast resolution during tick processing.
     * Useful to keep in memory or Redis cache.
     */
    public Map<Long, InstrumentDto> buildTokenMap(List<InstrumentDto> instruments) {
        return instruments.stream()
            .collect(Collectors.toMap(
                InstrumentDto::getInstrumentToken,
                i -> i,
                (existing, dupe) -> existing   // keep first on duplicate token
            ));
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private List<InstrumentDto> mapToDto(List<Instrument> instruments, Exchange exchange) {
        List<InstrumentDto> result = new ArrayList<>();

        for (Instrument inst : instruments) {
            try {
                result.add(InstrumentDto.builder()
                    .instrumentToken((long) inst.instrument_token)
                    .tradingSymbol(inst.tradingsymbol)
                    .name(inst.name)
                    .exchange(exchange)
                    .segment(inst.segment)
                    .instrumentType(inst.instrument_type)
                    .tickSize(BigDecimal.valueOf(inst.tick_size))
                    .lotSize((int) inst.lot_size)
                    .strikePrice(inst.strike != null && !inst.strike.isEmpty() ? new BigDecimal(inst.strike) : BigDecimal.ZERO)
                    .expiry(inst.expiry != null ? inst.expiry.toString() : null)
                    .build());
            } catch (Exception e) {
                log.warn("Skipping instrument {} due to mapping error: {}",
                    inst.tradingsymbol, e.getMessage());
            }
        }

        return result;
    }
}
