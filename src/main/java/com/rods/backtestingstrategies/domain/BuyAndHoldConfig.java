package com.rods.backtestingstrategies.domain;

public record BuyAndHoldConfig() implements StrategyConfig {

    @Override
    public StrategyType type() {
        return StrategyType.BUY_AND_HOLD;
    }

    @Override
    public int warmupCandles() {
        return 0;
    }
}
