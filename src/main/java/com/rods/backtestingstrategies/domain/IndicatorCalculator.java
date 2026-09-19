package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Linear-time indicator calculations over already validated, ordered candles. */
public final class IndicatorCalculator {

    private IndicatorCalculator() {}

    public static List<BigDecimal> sma(List<Candle> candles, int period) {
        validatePeriod(candles, period, "SMA");
        List<BigDecimal> values = nullSeries(candles.size());
        BigDecimal rollingSum = Decimal.ZERO;
        for (int index = 0; index < candles.size(); index++) {
            rollingSum = rollingSum.add(candles.get(index).close());
            if (index >= period) {
                rollingSum = rollingSum.subtract(candles.get(index - period).close());
            }
            if (index >= period - 1) {
                values.set(index, Decimal.divide(rollingSum, BigDecimal.valueOf(period)));
            }
        }
        return immutableNullableSeries(values);
    }

    /** Wilder RSI: initial average gain/loss followed by recursive Wilder smoothing. */
    public static List<BigDecimal> wilderRsi(List<Candle> candles, int period) {
        validatePeriod(candles, period, "RSI");
        if (candles.size() <= period) {
            return immutableNullableSeries(nullSeries(candles.size()));
        }
        List<BigDecimal> values = nullSeries(candles.size());
        BigDecimal gains = Decimal.ZERO;
        BigDecimal losses = Decimal.ZERO;
        for (int index = 1; index <= period; index++) {
            BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
            if (change.signum() > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        BigDecimal averageGain = Decimal.divide(gains, BigDecimal.valueOf(period));
        BigDecimal averageLoss = Decimal.divide(losses, BigDecimal.valueOf(period));
        values.set(period, rsi(averageGain, averageLoss));

        for (int index = period + 1; index < candles.size(); index++) {
            BigDecimal change = candles.get(index).close().subtract(candles.get(index - 1).close());
            BigDecimal gain = change.signum() > 0 ? change : Decimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.abs() : Decimal.ZERO;
            averageGain = averageGain.multiply(BigDecimal.valueOf(period - 1)).add(gain)
                    .divide(BigDecimal.valueOf(period), Decimal.SCALE, Decimal.ROUNDING);
            averageLoss = averageLoss.multiply(BigDecimal.valueOf(period - 1)).add(loss)
                    .divide(BigDecimal.valueOf(period), Decimal.SCALE, Decimal.ROUNDING);
            values.set(index, rsi(averageGain, averageLoss));
        }
        return immutableNullableSeries(values);
    }

    /** Standard EMA seeded with the period SMA; entries before the seed are null. */
    public static List<BigDecimal> ema(List<Candle> candles, int period) {
        validatePeriod(candles, period, "EMA");
        List<BigDecimal> closes = candles.stream().map(Candle::close).toList();
        return emaValues(closes, period);
    }

    public static MacdSeries macd(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0 || fastPeriod >= slowPeriod) {
            throw new DomainValidationException("MACD requires positive periods where fastPeriod < slowPeriod");
        }
        List<BigDecimal> fast = ema(candles, fastPeriod);
        List<BigDecimal> slow = ema(candles, slowPeriod);
        List<BigDecimal> line = nullSeries(candles.size());
        for (int index = 0; index < candles.size(); index++) {
            if (fast.get(index) != null && slow.get(index) != null) {
                line.set(index, fast.get(index).subtract(slow.get(index)).setScale(Decimal.SCALE, Decimal.ROUNDING));
            }
        }
        List<BigDecimal> signal = emaValues(line, signalPeriod);
        return new MacdSeries(immutableNullableSeries(fast), immutableNullableSeries(slow), immutableNullableSeries(line), signal);
    }

    private static List<BigDecimal> emaValues(List<BigDecimal> values, int period) {
        List<BigDecimal> result = nullSeries(values.size());
        int first = firstNonNull(values);
        if (first < 0 || values.size() - first < period) {
            return immutableNullableSeries(result);
        }
        BigDecimal sum = Decimal.ZERO;
        for (int index = first; index < first + period; index++) {
            sum = sum.add(values.get(index));
        }
        int seedIndex = first + period - 1;
        BigDecimal previous = Decimal.divide(sum, BigDecimal.valueOf(period));
        result.set(seedIndex, previous);
        BigDecimal multiplier = Decimal.divide(new BigDecimal("2"), BigDecimal.valueOf(period + 1L));
        for (int index = seedIndex + 1; index < values.size(); index++) {
            BigDecimal value = values.get(index);
            if (value == null) {
                continue;
            }
            previous = value.subtract(previous).multiply(multiplier).add(previous).setScale(Decimal.SCALE, Decimal.ROUNDING);
            result.set(index, previous);
        }
        return immutableNullableSeries(result);
    }

    private static BigDecimal rsi(BigDecimal averageGain, BigDecimal averageLoss) {
        if (averageLoss.signum() == 0) {
            return averageGain.signum() == 0 ? new BigDecimal("50.00000000") : new BigDecimal("100.00000000");
        }
        if (averageGain.signum() == 0) {
            return Decimal.ZERO;
        }
        BigDecimal relativeStrength = Decimal.divide(averageGain, averageLoss);
        return Decimal.HUNDRED.subtract(Decimal.divide(Decimal.HUNDRED, Decimal.ONE.add(relativeStrength)))
                .setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    private static List<BigDecimal> nullSeries(int size) {
        return new ArrayList<>(Collections.nCopies(size, null));
    }

    private static List<BigDecimal> immutableNullableSeries(List<BigDecimal> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static int firstNonNull(List<BigDecimal> values) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) != null) {
                return index;
            }
        }
        return -1;
    }

    private static void validatePeriod(List<Candle> candles, int period, String name) {
        if (period <= 0) {
            throw new DomainValidationException(name + " period must be positive");
        }
        if (candles == null || candles.isEmpty()) {
            throw new DomainValidationException("candles are required for " + name);
        }
    }

    public record MacdSeries(
            List<BigDecimal> fastEma,
            List<BigDecimal> slowEma,
            List<BigDecimal> macd,
            List<BigDecimal> signal) {}
}
