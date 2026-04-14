package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of comparing multiple strategies on the same stock data.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyComparisonResult {

    private String symbol;
    private double initialCapital;

    // Individual results keyed by strategy name
    private Map<String, BacktestResult> results;

    // Ranking: best to worst by return %
    private List<String> rankByReturn;

    // Ranking: best to worst by Sharpe Ratio
    private List<String> rankBySharpe;

    // Best overall strategy name
    private String bestStrategy;
}
