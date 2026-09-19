package com.rods.backtestingstrategies.domain;

public record MacdConfig(int fastPeriod, int slowPeriod, int signalPeriod) implements StrategyConfig {

    public MacdConfig {
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0 || fastPeriod >= slowPeriod) {
            throw new DomainValidationException("MACD requires positive periods where fastPeriod < slowPeriod");
        }
    }

    public static MacdConfig standard() {
        return new MacdConfig(12, 26, 9);
    }

    @Override
    public StrategyType type() {
        return StrategyType.MACD;
    }

    @Override
    public int warmupCandles() {
        return slowPeriod + signalPeriod - 1;
    }
}
