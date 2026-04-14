package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of portfolio-level backtesting.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioResult {

    private double totalCapital;
    private double finalValue;
    private double totalPnL;
    private double totalReturnPct;
    private String strategyUsed;

    // Aggregate performance metrics across the portfolio
    private PerformanceMetrics aggregateMetrics;

    // Per-symbol breakdown
    private Map<String, BacktestResult> symbolResults;

    // Allocation info
    private Map<String, Double> allocations; // symbol → allocated capital
}
