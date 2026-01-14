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
        // using the indexing fo performing faster queries and lowering the response time to the frontend
        indexes = {
                @Index(name = "idx_symbol", columnList = "symbol"),
                @Index(name = "idx_name", columnList = "name")
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. AAPL, TSLA
    @Column(nullable = false, length = 20)
    private String symbol;

    // e.g. Apple Inc
    @Column(nullable = false, length = 255)
    private String name;

    // Equity, ETF, Mutual Fund, Crypto
    @Column(length = 50)
    private String type;

    // United States, India, etc.
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

    // AlphaVantage match score
    @Column
    private Double matchScore;

    // When this symbol was last fetched/refreshed from API
    @Column(nullable = false)
    private LocalDateTime lastFetched;

    // Optional: source tracking
    @Column(length = 50)
    private String source = "ALPHAVANTAGE";
}