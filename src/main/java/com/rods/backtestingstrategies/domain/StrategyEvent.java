package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/** A decision made after observing a completed candle, not an executable fill. */
public record StrategyEvent(
        SignalAction action,
        LocalDate signalDate,
        BigDecimal observedPrice,
        StrategyType strategyType,
        Map<String, BigDecimal> indicators) {

    public StrategyEvent {
        action = Objects.requireNonNull(action, "action is required");
        signalDate = Objects.requireNonNull(signalDate, "signalDate is required");
        observedPrice = Decimal.value(observedPrice, "observedPrice");
        strategyType = Objects.requireNonNull(strategyType, "strategyType is required");
        indicators = Map.copyOf(Objects.requireNonNull(indicators, "indicators is required"));
    }

    public static StrategyEvent hold(Candle candle, StrategyType strategyType, Map<String, BigDecimal> indicators) {
        return new StrategyEvent(SignalAction.HOLD, candle.date(), candle.close(), strategyType, indicators);
    }
}
