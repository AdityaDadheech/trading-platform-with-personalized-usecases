package com.trading.aggregator.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * CRUD operations for the watchlist table.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WatchlistRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> findAll() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM watchlist ORDER BY display_order ASC"
        );
    }

    public void add(long instrumentToken, String symbol, String exchange) {
        jdbcTemplate.update("""
            INSERT INTO watchlist (instrument_token, trading_symbol, exchange, display_order)
            VALUES (?, ?, ?, (SELECT COALESCE(MAX(display_order), 0) + 1 FROM watchlist))
            ON CONFLICT (instrument_token) DO NOTHING
            """,
            instrumentToken, symbol, exchange
        );
    }

    public void remove(long instrumentToken) {
        jdbcTemplate.update(
            "DELETE FROM watchlist WHERE instrument_token = ?",
            instrumentToken
        );
    }

    public boolean exists(long instrumentToken) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM watchlist WHERE instrument_token = ?",
            Integer.class, instrumentToken
        );
        return count != null && count > 0;
    }
}