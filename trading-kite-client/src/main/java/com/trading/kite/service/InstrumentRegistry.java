package com.trading.kite.service;

import com.trading.common.dto.InstrumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry mapping instrumentToken → InstrumentDto.
 *
 * WHY IN-MEMORY:
 * TickNormalizerService calls getSymbol() on every single tick —
 * potentially 250+ times/second during market hours.
 * A DB or Redis lookup at that frequency would be too slow.
 * ConcurrentHashMap lookup is O(1) and takes ~10 nanoseconds.
 *
 * WHY ConcurrentHashMap specifically:
 * The WebSocket thread reads this map constantly (onTicks).
 * The startup/refresh thread writes to it once.
 * ConcurrentHashMap allows safe concurrent reads without locking.
 * Regular HashMap would risk corrupt reads during a write.
 *
 * LIFECYCLE:
 * - At startup: loads only if already authenticated (token in config)
 * - After auth: AuthController triggers load() manually
 * - Daily refresh: can be triggered by a scheduler (Phase 2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentRegistry {

    private final KiteInstrumentService kiteInstrumentService;
    private final KiteAuthService kiteAuthService;

    private final Map<Long, InstrumentDto> tokenMap = new ConcurrentHashMap<>();

    /**
     * Fires after all Spring beans are ready.
     * Only loads if we already have a valid access token (set in application-dev.yml).
     * If not authenticated yet, AuthController.callback() will trigger load() after login.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (!kiteAuthService.isAuthenticated()) {
            log.info("InstrumentRegistry: not authenticated at startup — will load after login.");
            return;
        }
        load();
    }

    /**
     * Downloads all NSE + NFO instruments from Kite and indexes them by token.
     * Called after successful auth, and can be called daily to refresh.
     */
    public void load() {
        try {
            log.info("Loading instrument registry from Kite...");
            List<InstrumentDto> instruments = kiteInstrumentService.fetchAllInstruments();
            tokenMap.clear();
            instruments.forEach(i -> tokenMap.put(i.getInstrumentToken(), i));
            log.info("InstrumentRegistry ready — {} instruments indexed", tokenMap.size());
        } catch (Exception e) {
            log.error("Failed to load instrument registry: {}", e.getMessage(), e);
        }
    }

    /**
     * Search instruments by symbol or company name.
     * Case-insensitive prefix/contains match.
     * Returns NSE equity instruments by default.
     */
    public List<InstrumentDto> search(String query, String exchange, int limit) {
        String q = query.toUpperCase().trim();
        return tokenMap.values().stream()
                .filter(i -> i.getExchange().name().equals(exchange))
                .filter(i -> i.getInstrumentType() != null &&
                        i.getInstrumentType().equals("EQ"))  // equity only
                .filter(i -> i.getTradingSymbol().contains(q) ||
                        (i.getName() != null && i.getName().toUpperCase().contains(q)))
                .sorted((a, b) -> {
                    // Exact prefix match ranks higher
                    boolean aPrefix = a.getTradingSymbol().startsWith(q);
                    boolean bPrefix = b.getTradingSymbol().startsWith(q);
                    if (aPrefix && !bPrefix) return -1;
                    if (!aPrefix && bPrefix) return 1;
                    return a.getTradingSymbol().compareTo(b.getTradingSymbol());
                })
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    public String getSymbol(long token) {
        InstrumentDto dto = tokenMap.get(token);
        return dto != null ? dto.getTradingSymbol() : null;
    }

    public String getExchange(long token) {
        InstrumentDto dto = tokenMap.get(token);
        return dto != null ? dto.getExchange().name() : null;
    }

    public boolean isLoaded() {
        return !tokenMap.isEmpty();
    }

    public int size() {
        return tokenMap.size();
    }
}