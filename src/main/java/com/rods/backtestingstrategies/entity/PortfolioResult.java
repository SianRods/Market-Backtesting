package com.rods.backtestingstrategies.entity;

import com.rods.backtestingstrategies.domain.BacktestResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Transitional endpoint result; Phase 5 replaces this with currency-safe portfolio accounting. */
public record PortfolioResult(
        BigDecimal totalCapital,
        BigDecimal finalValue,
        BigDecimal totalPnl,
        BigDecimal totalReturnPercent,
        String strategyUsed,
        Map<String, BacktestResult> symbolResults,
        Map<String, BigDecimal> allocations,
        List<String> warnings) {

    public PortfolioResult {
        symbolResults = Map.copyOf(symbolResults);
        allocations = Map.copyOf(allocations);
        warnings = List.copyOf(warnings);
    }
}
