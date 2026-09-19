package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StrategyComparisonResult(
        String symbol,
        BigDecimal initialCapital,
        Map<StrategyType, BacktestResult> results,
        List<StrategyType> rankByReturn,
        List<StrategyType> rankBySharpe,
        StrategyType bestStrategy,
        int measurementStartIndex) {

    public StrategyComparisonResult {
        results = Map.copyOf(results);
        rankByReturn = List.copyOf(rankByReturn);
        rankBySharpe = List.copyOf(rankBySharpe);
    }
}
