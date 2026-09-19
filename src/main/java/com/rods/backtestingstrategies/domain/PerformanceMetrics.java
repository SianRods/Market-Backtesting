package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.util.List;

/** Nullable values represent mathematically undefined metrics and are explained by warnings. */
public record PerformanceMetrics(
        BigDecimal totalReturnPercent,
        BigDecimal cagrPercent,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdownPercent,
        BigDecimal profitFactor,
        BigDecimal winLossRatio,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalFees,
        BigDecimal turnover,
        int completedTrades,
        int openPositions,
        int winningTrades,
        int losingTrades,
        int breakevenTrades,
        BigDecimal averageHoldingDays,
        List<String> warnings) {

    public PerformanceMetrics {
        warnings = List.copyOf(warnings);
    }
}
