package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;

public record RsiConfig(int period, BigDecimal oversold, BigDecimal overbought) implements StrategyConfig {

    public RsiConfig {
        if (period <= 0) {
            throw new DomainValidationException("RSI period must be positive");
        }
        oversold = Decimal.value(oversold, "oversold");
        overbought = Decimal.value(overbought, "overbought");
        if (oversold.signum() < 0 || overbought.compareTo(Decimal.HUNDRED) > 0 || oversold.compareTo(overbought) >= 0) {
            throw new DomainValidationException("RSI thresholds must satisfy 0 <= oversold < overbought <= 100");
        }
    }

    public static RsiConfig standard() {
        return new RsiConfig(14, new BigDecimal("30"), new BigDecimal("70"));
    }

    @Override
    public StrategyType type() {
        return StrategyType.RSI;
    }

    @Override
    public int warmupCandles() {
        return period + 1;
    }
}
