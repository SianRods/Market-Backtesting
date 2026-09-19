package com.rods.backtestingstrategies.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rods.backtestingstrategies.PostgresIntegrationTest;
import com.rods.backtestingstrategies.repository.CandleIngestionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Docker-deferred contract tests for Flyway's PostgreSQL schema and correction-safe upserts. */
@SpringBootTest
@ActiveProfiles("test")
class MarketDataPersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CandleIngestionRepository ingestionRepository;

    @Autowired
    private CandleNormalizer normalizer;

    @Test
    void appliesMarketDataMigrationsAndSearchIndexes() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);

        assertTrue(versions.containsAll(List.of("1", "2", "3")));
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE tablename IN ('candles', 'stock_symbols')
                  AND indexname IN ('idx_candles_provider_symbol_date', 'idx_candles_symbol_date_range',
                                   'idx_stock_symbols_lower_symbol', 'idx_stock_symbols_name_trgm')
                """, Integer.class);
        assertEquals(4, indexCount);
    }

    @Test
    @Transactional
    void upsertReplacesCorrectedCandleWithoutDuplicatingTheProviderSession() {
        DailyCandleResponse original = response("10.00", Instant.parse("2024-01-03T00:00:00Z"));
        var originalBatch = ingestionRepository.startBatch(original, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));
        ingestionRepository.upsert(original, normalizer.normalize(original, AdjustmentPolicy.RAW_OHLC, null),
                AdjustmentPolicy.RAW_OHLC, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), originalBatch);

        DailyCandleResponse corrected = response("11.00", Instant.parse("2024-01-04T00:00:00Z"));
        var correctionBatch = ingestionRepository.startBatch(corrected, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));
        ingestionRepository.upsert(corrected, normalizer.normalize(corrected, AdjustmentPolicy.RAW_OHLC, null),
                AdjustmentPolicy.RAW_OHLC, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), correctionBatch);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM candles WHERE provider = 'YAHOO' AND symbol = 'PHASE2TEST' AND date = DATE '2024-01-02'",
                Integer.class);
        BigDecimal close = jdbcTemplate.queryForObject(
                "SELECT raw_close FROM candles WHERE provider = 'YAHOO' AND symbol = 'PHASE2TEST' AND date = DATE '2024-01-02'",
                BigDecimal.class);

        assertEquals(1, count);
        assertEquals(new BigDecimal("11.00000000"), close);
    }

    private DailyCandleResponse response(String close, Instant fetchedAt) {
        BigDecimal closingPrice = new BigDecimal(close);
        return new DailyCandleResponse("PHASE2TEST", List.of(new ProviderDailyCandle(
                Instant.parse("2024-01-02T14:30:00Z"), closingPrice, closingPrice.add(BigDecimal.ONE),
                closingPrice.subtract(BigDecimal.ONE), closingPrice, closingPrice, 100)), ZoneId.of("America/New_York"), null,
                "USD", "EQUITY", true, fetchedAt,
                new ProviderMetadata("YAHOO", "integration-test", Set.of(ProviderCapability.DAILY_OHLCV)));
    }
}
