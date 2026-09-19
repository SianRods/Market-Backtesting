CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255)
);

CREATE TABLE candles (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(255),
    date DATE,
    open_price DOUBLE PRECISION NOT NULL,
    high_price DOUBLE PRECISION NOT NULL,
    low_price DOUBLE PRECISION NOT NULL,
    close_price DOUBLE PRECISION NOT NULL,
    volume BIGINT NOT NULL,
    CONSTRAINT uk_candles_symbol_date UNIQUE (symbol, date)
);

CREATE TABLE stock_symbols (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    exchange VARCHAR(50),
    region VARCHAR(100),
    market_open VARCHAR(10),
    market_close VARCHAR(10),
    timezone VARCHAR(64),
    currency VARCHAR(10),
    sector VARCHAR(100),
    industry VARCHAR(150),
    match_score DOUBLE PRECISION,
    last_fetched TIMESTAMP NOT NULL,
    source VARCHAR(50)
);
