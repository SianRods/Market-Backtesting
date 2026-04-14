package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Advanced performance metrics for a backtest result.
 * Calculated from equity curve and transaction history.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceMetrics {

    // Risk-adjusted return (annualized)
    private double sharpeRatio;

    // Worst peak-to-trough decline (as negative %)
    private double maxDrawdown;

    // Percentage of profitable trades
    private double winRate;

    // Average profit on winning trades
    private double avgWin;

    // Average loss on losing trades
    private double avgLoss;

    // Ratio of average win to average loss
    private double winLossRatio;

    // Total number of trades executed
    private int totalTrades;

    // Number of winning trades
    private int winningTrades;

    // Number of losing trades
    private int losingTrades;

    // Annualized return percentage
    private double annualizedReturn;

    // Profit factor: gross profit / gross loss
    private double profitFactor;

    // Average holding period in days
    private double avgHoldingPeriodDays;
}
