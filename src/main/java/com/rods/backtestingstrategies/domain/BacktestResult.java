package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.util.List;

public record BacktestResult(
        String symbol,
        StrategyConfig strategyConfig,
        ExecutionModel executionModel,
        BigDecimal startCapital,
        BigDecimal finalCapital,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalPnl,
        BigDecimal returnPercent,
        List<EquityPoint> equityCurve,
        List<StrategyEvent> strategyEvents,
        List<Fill> fills,
        List<CompletedTrade> completedTrades,
        List<String> warnings,
        PerformanceMetrics metrics) {

    public BacktestResult {
        equityCurve = List.copyOf(equityCurve);
        strategyEvents = List.copyOf(strategyEvents);
        fills = List.copyOf(fills);
        completedTrades = List.copyOf(completedTrades);
        warnings = List.copyOf(warnings);
    }
}
