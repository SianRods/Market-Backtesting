package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "stock_symbols",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"symbol"})
        },
        indexes = {
                @Index(name = "idx_symbol", columnList = "symbol"),
                @Index(name = "idx_name", columnList = "name"),
                @Index(name = "idx_exchange", columnList = "exchange"),
                @Index(name = "idx_sector", columnList = "sector")
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. AAPL, TSLA, RELIANCE.NS
    @Column(nullable = false, length = 20)
    private String symbol;

    // e.g. Apple Inc, Reliance Industries
    @Column(nullable = false, length = 255)
    private String name;

    // Equity, ETF, Index
    @Column(length = 50)
    private String type;

    // Exchange name: NASDAQ, NYSE, BSE, NSE
    @Column(length = 50)
    private String exchange;

    // Country / Region
    @Column(length = 100)
    private String region;

    @Column(length = 10)
    private String marketOpen;

    @Column(length = 10)
    private String marketClose;

    @Column(length = 20)
    private String timezone;

    @Column(length = 10)
    private String currency;

    // Sector: Technology, Finance, Healthcare, etc.
    @Column(length = 100)
    private String sector;

    // Industry sub-category
    @Column(length = 150)
    private String industry;

    // Search relevance score (1.0 = exact match)
    @Column
    private Double matchScore;

    // When this symbol was last fetched/refreshed
    @Column(nullable = false)
    private LocalDateTime lastFetched;

    // Data source tracker
    @Column(length = 50)
    private String source = "TICKER_SEED";
}