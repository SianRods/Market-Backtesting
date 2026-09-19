ALTER TABLE candles ADD CONSTRAINT ck_candles_positive_ohlc
    CHECK (raw_open > 0 AND raw_high > 0 AND raw_low > 0 AND raw_close > 0) NOT VALID;
ALTER TABLE candles ADD CONSTRAINT ck_candles_ohlc_range
    CHECK (raw_low <= raw_open AND raw_low <= raw_close AND raw_high >= raw_open AND raw_high >= raw_close) NOT VALID;
ALTER TABLE candles ADD CONSTRAINT ck_candles_nonnegative_volume CHECK (volume >= 0) NOT VALID;
ALTER TABLE candles ADD CONSTRAINT ck_candles_adjustment_factor
    CHECK (adjustment_factor IS NULL OR adjustment_factor > 0) NOT VALID;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_stock_symbols_lower_symbol ON stock_symbols (LOWER(symbol));
CREATE INDEX idx_stock_symbols_name_trgm ON stock_symbols USING GIN (LOWER(name) gin_trgm_ops);
