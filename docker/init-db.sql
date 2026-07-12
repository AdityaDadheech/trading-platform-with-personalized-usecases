-- ─────────────────────────────────────────────────────────────────────────────
-- Trading Platform — Database Initialization
-- Runs once automatically when TimescaleDB container first starts.
-- ─────────────────────────────────────────────────────────────────────────────

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ── instruments ───────────────────────────────────────────────────────────────
-- Master list of all tradable NSE/NFO instruments.
-- Refreshed daily from Kite's instrument CSV dump.
CREATE TABLE IF NOT EXISTS instruments (
    id                 BIGSERIAL PRIMARY KEY,
    instrument_token   BIGINT        NOT NULL UNIQUE,
    trading_symbol     VARCHAR(50)   NOT NULL,
    name               VARCHAR(200),
    exchange           VARCHAR(10)   NOT NULL,   -- NSE, NFO, BSE, MCX
    segment            VARCHAR(20),              -- NSE, NFO-FUT, NFO-OPT
    instrument_type    VARCHAR(10),              -- EQ, FUT, CE, PE
    tick_size          NUMERIC(10,2),
    lot_size           INTEGER,
    strike_price       NUMERIC(12,2),
    expiry             DATE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_instruments_symbol
    ON instruments(trading_symbol);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange
    ON instruments(exchange);

-- ── ohlcv_candles ─────────────────────────────────────────────────────────────
-- OHLCV candlestick data for all intervals (1m, 5m, 15m, 1d, etc.)
-- This is a TimescaleDB hypertable partitioned by bucket_time.
CREATE TABLE IF NOT EXISTS ohlcv_candles (
    id                 BIGSERIAL,
    instrument_token   BIGINT        NOT NULL,
    trading_symbol     VARCHAR(50)   NOT NULL,
    exchange           VARCHAR(10)   NOT NULL,
    interval           VARCHAR(10)   NOT NULL,   -- minute, 5minute, day, etc.
    bucket_time        TIMESTAMPTZ   NOT NULL,   -- candle open time
    open               NUMERIC(12,4) NOT NULL,
    high               NUMERIC(12,4) NOT NULL,
    low                NUMERIC(12,4) NOT NULL,
    close              NUMERIC(12,4) NOT NULL,
    volume             BIGINT        NOT NULL DEFAULT 0,
    open_interest      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id, bucket_time)
);

-- Convert to TimescaleDB hypertable (partitioned by bucket_time, 1-day chunks)
-- This gives automatic time-based partitioning, compression, and fast range queries.
SELECT create_hypertable(
    'ohlcv_candles',
    'bucket_time',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

-- Unique constraint: one candle per symbol+interval+time
CREATE UNIQUE INDEX IF NOT EXISTS idx_ohlcv_unique
    ON ohlcv_candles(instrument_token, interval, bucket_time);

CREATE INDEX IF NOT EXISTS idx_ohlcv_symbol_interval
    ON ohlcv_candles(trading_symbol, interval, bucket_time DESC);

-- ── watchlist ─────────────────────────────────────────────────────────────────
-- User's saved watchlist of instruments.
CREATE TABLE IF NOT EXISTS watchlist (
    id                 BIGSERIAL PRIMARY KEY,
    instrument_token   BIGINT        NOT NULL,
    trading_symbol     VARCHAR(50)   NOT NULL,
    exchange           VARCHAR(10)   NOT NULL,
    display_order      INTEGER       NOT NULL DEFAULT 0,
    added_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(instrument_token)
);

-- ── Seed some default watchlist entries ───────────────────────────────────────
INSERT INTO watchlist (instrument_token, trading_symbol, exchange, display_order)
VALUES
    (256265,  'NIFTY 50',  'NSE', 1),
    (260105,  'NIFTY BANK','NSE', 2),
    (408065,  'RELIANCE',  'NSE', 3),
    (738561,  'INFY',      'NSE', 4),
    (341249,  'TCS',       'NSE', 5)
ON CONFLICT (instrument_token) DO NOTHING;
