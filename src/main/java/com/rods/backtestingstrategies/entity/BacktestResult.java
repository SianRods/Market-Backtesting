package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    // change it depending upon the frontend requirements
    private double startCapital;
    private double finalCapital;
    private double profitLoss;
    private double returnPct;

    // time-series for charting
    private List<EquityPoint> equityCurve;

    // explicit buy / sell operations
    private List<Transaction> transactions;

    // crossover events (bull/bear)
    private List<CrossOver> crossovers;
}
