package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.marketdata.AdjustmentPolicy;
import com.rods.backtestingstrategies.marketdata.DailyCandleResponse;
import com.rods.backtestingstrategies.marketdata.NormalizedCandle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CandleIngestionRepository {

    private static final String UPSERT = """
            INSERT INTO candles (provider, symbol, date, raw_open, raw_high, raw_low, raw_close, adjusted_close,
                adjustment_factor, adjustment_policy, volume, exchange_timezone, currency, instrument_type, fetched_at,
                requested_start, requested_end, ingestion_batch_id, dataset_hash, adapter_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (provider, symbol, date) DO UPDATE SET
                raw_open = EXCLUDED.raw_open, raw_high = EXCLUDED.raw_high, raw_low = EXCLUDED.raw_low,
                raw_close = EXCLUDED.raw_close, adjusted_close = EXCLUDED.adjusted_close,
                adjustment_factor = EXCLUDED.adjustment_factor, adjustment_policy = EXCLUDED.adjustment_policy,
                volume = EXCLUDED.volume, exchange_timezone = EXCLUDED.exchange_timezone, currency = EXCLUDED.currency,
                instrument_type = EXCLUDED.instrument_type, fetched_at = EXCLUDED.fetched_at,
                requested_start = EXCLUDED.requested_start, requested_end = EXCLUDED.requested_end,
                ingestion_batch_id = EXCLUDED.ingestion_batch_id, dataset_hash = EXCLUDED.dataset_hash,
                adapter_version = EXCLUDED.adapter_version
            """;

    private final JdbcTemplate jdbcTemplate;

    public CandleIngestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquireSymbolLock(String provider, String symbol) {
        Boolean acquired = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, provider + ':' + symbol);
        return Boolean.TRUE.equals(acquired);
    }

    public UUID startBatch(DailyCandleResponse response, LocalDate requestedStart, LocalDate requestedEnd) {
        UUID batchId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ingestion_batches (id, provider, requested_start, requested_end, fetched_at, adapter_version, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, batchId, response.providerMetadata().providerId(), requestedStart, requestedEnd,
                Timestamp.from(response.fetchedAt()), response.providerMetadata().adapterVersion(), "SUCCEEDED");
        return batchId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedBatch(String provider, LocalDate requestedStart, LocalDate requestedEnd, String adapterVersion,
            String failureType, String failureMessage) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_batches (id, provider, requested_start, requested_end, fetched_at, adapter_version, status, failure_type, failure_message)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)
                """, UUID.randomUUID(), provider, requestedStart, requestedEnd, adapterVersion, "FAILED", failureType,
                failureMessage == null ? null : failureMessage.substring(0, Math.min(failureMessage.length(), 512)));
    }

    public void upsert(
            DailyCandleResponse response,
            List<NormalizedCandle> candles,
            AdjustmentPolicy policy,
            LocalDate requestedStart,
            LocalDate requestedEnd,
            UUID batchId) {
        jdbcTemplate.batchUpdate(UPSERT, candles, 250, (PreparedStatement statement, NormalizedCandle candle) -> {
            statement.setString(1, response.providerMetadata().providerId());
            statement.setString(2, candle.domainCandle().symbol());
            statement.setObject(3, candle.sessionDate());
            statement.setBigDecimal(4, candle.rawOpen());
            statement.setBigDecimal(5, candle.rawHigh());
            statement.setBigDecimal(6, candle.rawLow());
            statement.setBigDecimal(7, candle.rawClose());
            statement.setBigDecimal(8, candle.adjustedClose());
            statement.setBigDecimal(9, candle.adjustmentFactor());
            statement.setString(10, policy.name());
            statement.setLong(11, candle.volume());
            statement.setString(12, response.exchangeTimezone() == null ? null : response.exchangeTimezone().getId());
            statement.setString(13, response.currency());
            statement.setString(14, response.instrumentType());
            statement.setTimestamp(15, Timestamp.from(response.fetchedAt()));
            statement.setObject(16, requestedStart);
            statement.setObject(17, requestedEnd);
            statement.setObject(18, batchId);
            statement.setString(19, hash(response, candle, policy));
            statement.setString(20, response.providerMetadata().adapterVersion());
        });
    }

    private String hash(DailyCandleResponse response, NormalizedCandle candle, AdjustmentPolicy policy) {
        String value = String.join("|", response.providerMetadata().providerId(), response.providerMetadata().adapterVersion(),
                candle.domainCandle().symbol(), candle.sessionDate().toString(), candle.rawOpen().toPlainString(),
                candle.rawHigh().toPlainString(), candle.rawLow().toPlainString(), candle.rawClose().toPlainString(),
                candle.adjustedClose() == null ? "ABSENT" : candle.adjustedClose().toPlainString(),
                candle.adjustmentFactor() == null ? "ABSENT" : candle.adjustmentFactor().toPlainString(), policy.name());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }
}
