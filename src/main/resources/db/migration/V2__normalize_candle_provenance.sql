ALTER TABLE candles RENAME COLUMN open_price TO raw_open;
ALTER TABLE candles RENAME COLUMN high_price TO raw_high;
ALTER TABLE candles RENAME COLUMN low_price TO raw_low;
ALTER TABLE candles RENAME COLUMN close_price TO raw_close;

ALTER TABLE candles ALTER COLUMN raw_open TYPE NUMERIC(20,8) USING raw_open::NUMERIC(20,8);
ALTER TABLE candles ALTER COLUMN raw_high TYPE NUMERIC(20,8) USING raw_high::NUMERIC(20,8);
ALTER TABLE candles ALTER COLUMN raw_low TYPE NUMERIC(20,8) USING raw_low::NUMERIC(20,8);
ALTER TABLE candles ALTER COLUMN raw_close TYPE NUMERIC(20,8) USING raw_close::NUMERIC(20,8);

ALTER TABLE candles ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'YAHOO';
ALTER TABLE candles ADD COLUMN adjusted_close NUMERIC(20,8);
ALTER TABLE candles ADD COLUMN adjustment_factor NUMERIC(20,12);
ALTER TABLE candles ADD COLUMN adjustment_policy VARCHAR(32) NOT NULL DEFAULT 'RAW_OHLC';
ALTER TABLE candles ADD COLUMN exchange_timezone VARCHAR(64);
ALTER TABLE candles ADD COLUMN currency VARCHAR(16);
ALTER TABLE candles ADD COLUMN instrument_type VARCHAR(32);
ALTER TABLE candles ADD COLUMN fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE candles ADD COLUMN requested_start DATE;
ALTER TABLE candles ADD COLUMN requested_end DATE;
ALTER TABLE candles ADD COLUMN ingestion_batch_id UUID;
ALTER TABLE candles ADD COLUMN dataset_hash VARCHAR(64);
ALTER TABLE candles ADD COLUMN adapter_version VARCHAR(64) NOT NULL DEFAULT 'legacy-baseline';

CREATE TABLE ingestion_batches (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    requested_start DATE NOT NULL,
    requested_end DATE NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    adapter_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_type VARCHAR(64),
    failure_message VARCHAR(512)
);

INSERT INTO ingestion_batches (id, provider, requested_start, requested_end, fetched_at, adapter_version, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'YAHOO', DATE '1970-01-01', DATE '1970-01-01', CURRENT_TIMESTAMP, 'legacy-baseline', 'ADOPTED');

UPDATE candles
SET requested_start = date,
    requested_end = date,
    ingestion_batch_id = '00000000-0000-0000-0000-000000000001',
    dataset_hash = md5(provider || '|' || upper(symbol) || '|' || date::text || '|' || raw_open::text || '|' || raw_high::text || '|' || raw_low::text || '|' || raw_close::text || '|' || COALESCE(volume::text, '0'));

ALTER TABLE candles ALTER COLUMN requested_start SET NOT NULL;
ALTER TABLE candles ALTER COLUMN requested_end SET NOT NULL;
ALTER TABLE candles ALTER COLUMN ingestion_batch_id SET NOT NULL;
ALTER TABLE candles ALTER COLUMN dataset_hash SET NOT NULL;
ALTER TABLE candles DROP CONSTRAINT IF EXISTS uk_candles_symbol_date;
ALTER TABLE candles ADD CONSTRAINT uk_candles_provider_symbol_date UNIQUE (provider, symbol, date);
CREATE INDEX idx_candles_provider_symbol_date ON candles (provider, symbol, date);
CREATE INDEX idx_candles_symbol_date_range ON candles (symbol, date);
