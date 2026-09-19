package com.rods.backtestingstrategies.domain;

public record SmaConfig(int shortPeriod, int longPeriod) implements StrategyConfig {

    public SmaConfig {
        if (shortPeriod <= 0 || longPeriod <= 0 || shortPeriod >= longPeriod) {
            throw new DomainValidationException("SMA requires positive periods where shortPeriod < longPeriod");
        }
    }

    @Override
    public StrategyType type() {
        return StrategyType.SMA;
    }

    @Override
    public int warmupCandles() {
        return longPeriod;
    }
}
