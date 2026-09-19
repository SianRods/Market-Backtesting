package com.rods.backtestingstrategies.entity;

import com.rods.backtestingstrategies.marketdata.AdjustmentPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Persisted provider-normalized daily candle; Phase 1 domain candles are derived explicitly. */
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "candles",
        uniqueConstraints = @UniqueConstraint(name = "uk_candles_provider_symbol_date", columnNames = {"provider", "symbol", "date"}),
        indexes = @Index(name = "idx_candles_provider_symbol_date", columnList = "provider,symbol,date"))
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "raw_open", nullable = false, precision = 20, scale = 8)
    private BigDecimal rawOpen;

    @Column(name = "raw_high", nullable = false, precision = 20, scale = 8)
    private BigDecimal rawHigh;

    @Column(name = "raw_low", nullable = false, precision = 20, scale = 8)
    private BigDecimal rawLow;

    @Column(name = "raw_close", nullable = false, precision = 20, scale = 8)
    private BigDecimal rawClose;

    @Column(name = "adjusted_close", precision = 20, scale = 8)
    private BigDecimal adjustedClose;

    @Column(name = "adjustment_factor", precision = 20, scale = 12)
    private BigDecimal adjustmentFactor;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_policy", nullable = false, length = 32)
    private AdjustmentPolicy adjustmentPolicy;

    @Column(nullable = false)
    private long volume;

    @Column(name = "exchange_timezone", length = 64)
    private String exchangeTimezone;

    @Column(length = 16)
    private String currency;

    @Column(name = "instrument_type", length = 32)
    private String instrumentType;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "requested_start", nullable = false)
    private LocalDate requestedStart;

    @Column(name = "requested_end", nullable = false)
    private LocalDate requestedEnd;

    @Column(name = "ingestion_batch_id", nullable = false)
    private UUID ingestionBatchId;

    @Column(name = "dataset_hash", nullable = false, length = 64)
    private String datasetHash;

    @Column(name = "adapter_version", nullable = false, length = 64)
    private String adapterVersion;
}
