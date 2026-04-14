package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for portfolio-level backtesting.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioRequest {

    private List<PortfolioEntry> entries;
    private double totalCapital;
    private String strategy; // SMA, RSI, MACD, BUY_AND_HOLD

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PortfolioEntry {
        private String symbol;
        private double weight; // 0.0 to 1.0 (e.g., 0.4 = 40% allocation)
    }
}
