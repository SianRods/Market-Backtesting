package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) Strategy.
 *
 * MACD Line = EMA(fastPeriod) - EMA(slowPeriod)
 * Signal Line = EMA(signalPeriod) of MACD Line
 *
 * BUY → MACD crosses above Signal Line
 * SELL → MACD crosses below Signal Line
 */
@Component
public class MacdStrategy implements Strategy {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    public MacdStrategy() {
        // Standard MACD(12, 26, 9)
        this.fastPeriod = 12;
        this.slowPeriod = 26;
        this.signalPeriod = 9;
    }

    public MacdStrategy(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    @Override
    public TradeSignal evaluate(List<Candle> candles, int index) {

        // Need enough data: slowPeriod + signalPeriod candles minimum
        int minRequired = slowPeriod + signalPeriod;
        if (index < minRequired) {
            return TradeSignal.hold();
        }

        Candle candle = candles.get(index);

        // Calculate MACD and Signal for current and previous index
        double currMacd = calculateMacd(candles, index);
        double prevMacd = calculateMacd(candles, index - 1);

        double currSignal = calculateSignalLine(candles, index);
        double prevSignal = calculateSignalLine(candles, index - 1);

        // MACD crosses above Signal → BUY
        if (prevMacd <= prevSignal && currMacd > currSignal) {
            return TradeSignal.buy(candle);
        }

        // MACD crosses below Signal → SELL
        if (prevMacd >= prevSignal && currMacd < currSignal) {
            return TradeSignal.sell(candle);
        }

        return TradeSignal.hold();
    }

    /**
     * Calculate MACD line = EMA(fast) - EMA(slow)
     */
    private double calculateMacd(List<Candle> candles, int index) {
        double fastEma = calculateEma(candles, index, fastPeriod);
        double slowEma = calculateEma(candles, index, slowPeriod);
        return fastEma - slowEma;
    }

    /**
     * Calculate Signal line = EMA(signalPeriod) of MACD values
     */
    private double calculateSignalLine(List<Candle> candles, int index) {
        // We need 'signalPeriod' MACD values ending at 'index'
        double multiplier = 2.0 / (signalPeriod + 1);

        // Seed with the oldest MACD value in the window
        double signalEma = calculateMacd(candles, index - signalPeriod + 1);

        for (int i = index - signalPeriod + 2; i <= index; i++) {
            double macdValue = calculateMacd(candles, i);
            signalEma = (macdValue - signalEma) * multiplier + signalEma;
        }

        return signalEma;
    }

    /**
     * Calculate Exponential Moving Average at a given index.
     * Uses the standard EMA formula with SMA as the seed value.
     */
    private double calculateEma(List<Candle> candles, int index, int period) {
        if (index < period - 1) {
            // Not enough data, return SMA
            return sma(candles, index, Math.min(period, index + 1));
        }

        double multiplier = 2.0 / (period + 1);

        // Seed EMA with SMA of the first 'period' candles
        double ema = sma(candles, period - 1, period);

        // Calculate EMA from period to index
        for (int i = period; i <= index; i++) {
            double price = candles.get(i).getClosePrice();
            ema = (price - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * Simple Moving Average helper
     */
    private double sma(List<Candle> candles, int endIndex, int period) {
        double sum = 0.0;
        int start = Math.max(0, endIndex - period + 1);
        for (int i = start; i <= endIndex; i++) {
            sum += candles.get(i).getClosePrice();
        }
        return sum / (endIndex - start + 1);
    }

    @Override
    public String getName() {
        return "MACD (" + fastPeriod + ", " + slowPeriod + ", " + signalPeriod + ")";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.MACD;
    }
}
