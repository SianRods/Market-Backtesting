package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stateless signal generator. A signal only describes a decision at the observed close. */
public final class StrategyEvaluator {

    public List<StrategyEvent> evaluate(List<Candle> candles, StrategyConfig configuration) {
        validateHistory(candles, configuration);
        return switch (configuration) {
            case SmaConfig sma -> sma(candles, sma);
            case RsiConfig rsi -> rsi(candles, rsi);
            case MacdConfig macd -> macd(candles, macd);
            case BuyAndHoldConfig ignored -> buyAndHold(candles);
        };
    }

    private List<StrategyEvent> sma(List<Candle> candles, SmaConfig config) {
        List<BigDecimal> shortSma = IndicatorCalculator.sma(candles, config.shortPeriod());
        List<BigDecimal> longSma = IndicatorCalculator.sma(candles, config.longPeriod());
        List<StrategyEvent> events = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            Map<String, BigDecimal> diagnostic = diagnostics("shortSma", shortSma.get(index), "longSma", longSma.get(index));
            SignalAction action = SignalAction.HOLD;
            if (index > 0 && shortSma.get(index - 1) != null && longSma.get(index - 1) != null
                    && shortSma.get(index) != null && longSma.get(index) != null) {
                if (shortSma.get(index - 1).compareTo(longSma.get(index - 1)) <= 0
                        && shortSma.get(index).compareTo(longSma.get(index)) > 0) {
                    action = SignalAction.BUY;
                } else if (shortSma.get(index - 1).compareTo(longSma.get(index - 1)) >= 0
                        && shortSma.get(index).compareTo(longSma.get(index)) < 0) {
                    action = SignalAction.SELL;
                }
            }
            events.add(event(candles.get(index), action, StrategyType.SMA, diagnostic));
        }
        return List.copyOf(events);
    }

    private List<StrategyEvent> rsi(List<Candle> candles, RsiConfig config) {
        List<BigDecimal> values = IndicatorCalculator.wilderRsi(candles, config.period());
        List<StrategyEvent> events = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            BigDecimal rsi = values.get(index);
            SignalAction action = SignalAction.HOLD;
            if (rsi != null && rsi.compareTo(config.oversold()) < 0) {
                action = SignalAction.BUY;
            } else if (rsi != null && rsi.compareTo(config.overbought()) > 0) {
                action = SignalAction.SELL;
            }
            events.add(event(candles.get(index), action, StrategyType.RSI, diagnostics("rsi", rsi)));
        }
        return List.copyOf(events);
    }

    private List<StrategyEvent> macd(List<Candle> candles, MacdConfig config) {
        IndicatorCalculator.MacdSeries series = IndicatorCalculator.macd(candles, config.fastPeriod(), config.slowPeriod(), config.signalPeriod());
        List<StrategyEvent> events = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            BigDecimal currentMacd = series.macd().get(index);
            BigDecimal currentSignal = series.signal().get(index);
            SignalAction action = SignalAction.HOLD;
            if (index > 0 && series.macd().get(index - 1) != null && series.signal().get(index - 1) != null
                    && currentMacd != null && currentSignal != null) {
                if (series.macd().get(index - 1).compareTo(series.signal().get(index - 1)) <= 0
                        && currentMacd.compareTo(currentSignal) > 0) {
                    action = SignalAction.BUY;
                } else if (series.macd().get(index - 1).compareTo(series.signal().get(index - 1)) >= 0
                        && currentMacd.compareTo(currentSignal) < 0) {
                    action = SignalAction.SELL;
                }
            }
            events.add(event(candles.get(index), action, StrategyType.MACD,
                    diagnostics("macd", currentMacd, "signal", currentSignal)));
        }
        return List.copyOf(events);
    }

    private List<StrategyEvent> buyAndHold(List<Candle> candles) {
        List<StrategyEvent> events = new ArrayList<>();
        for (int index = 0; index < candles.size(); index++) {
            events.add(event(candles.get(index), index == 0 ? SignalAction.BUY : SignalAction.HOLD, StrategyType.BUY_AND_HOLD, Map.of()));
        }
        return List.copyOf(events);
    }

    private StrategyEvent event(Candle candle, SignalAction action, StrategyType type, Map<String, BigDecimal> diagnostics) {
        return new StrategyEvent(action, candle.date(), candle.close(), type, diagnostics);
    }

    private Map<String, BigDecimal> diagnostics(String firstName, BigDecimal firstValue) {
        return firstValue == null ? Map.of() : Map.of(firstName, firstValue);
    }

    private Map<String, BigDecimal> diagnostics(String firstName, BigDecimal firstValue, String secondName, BigDecimal secondValue) {
        if (firstValue == null || secondValue == null) {
            return Map.of();
        }
        return Map.of(firstName, firstValue, secondName, secondValue);
    }

    private void validateHistory(List<Candle> candles, StrategyConfig configuration) {
        if (candles == null || candles.isEmpty()) {
            throw new DomainValidationException("candle history is required");
        }
        if (candles.size() < configuration.warmupCandles()) {
            throw new DomainValidationException("insufficient history for " + configuration.type());
        }
        for (int index = 1; index < candles.size(); index++) {
            if (!candles.get(index).symbol().equals(candles.get(0).symbol())) {
                throw new DomainValidationException("all candles must use the same symbol");
            }
            if (!candles.get(index).date().isAfter(candles.get(index - 1).date())) {
                throw new DomainValidationException("candle dates must be strictly ordered and unique");
            }
        }
    }
}
