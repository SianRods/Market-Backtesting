package com.rods.backtestingstrategies.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
public class BacktestResult {

    // Capital metrics
    private final double startCapital;
    private final double finalCapital;
    private final double profitLoss;
    private final double returnPct;

    // Time-series equity curve
    private final List<EquityPoint> equityCurve;

    // Executed trades
    private final List<Transaction> transactions;

    // Bullish / Bearish crossover events
    private final List<CrossOver> crossovers;

    /* ==========================
       Factory Helpers
       ========================== */

    public static BacktestResult empty(double capital) {
        return BacktestResult.builder()
                .startCapital(capital)
                .finalCapital(capital)
                .profitLoss(0.0)
                .returnPct(0.0)
                .equityCurve(List.of())
                .transactions(List.of())
                .crossovers(List.of())
                .build();
    }
}
