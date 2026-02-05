package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MacdStrategy implements Strategy {

    private static final int SHORT_PERIOD = 12;
    private static final int LONG_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;

    @Override
    public TradeSignal evaluate(List<Candle> candles, int index) {
        // we need at least enough data to have a valid signal line
        // roughly long_period + signal_period
        if (index < LONG_PERIOD + SIGNAL_PERIOD) {
            return TradeSignal.hold();
        }

        double currMacd = calculateMacd(candles, index);
        double currSignal = calculateSignalLine(candles, index);

        double prevMacd = calculateMacd(candles, index - 1);
        double prevSignal = calculateSignalLine(candles, index - 1);

        Candle currentCandle = candles.get(index);

        // Crossover Logic
        // MACD crosses ABOVE Signal -> BUY
        if (prevMacd <= prevSignal && currMacd > currSignal) {
            return TradeSignal.buy(currentCandle);
        }

        // MACD crosses BELOW Signal -> SELL
        if (prevMacd >= prevSignal && currMacd < currSignal) {
            return TradeSignal.sell(currentCandle);
        }

        return TradeSignal.hold();
    }

    @Override
    public String getName() {
        return "MACD (12, 26, 9)";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.MACD;
    }

    // --- Helpers ---

    private double calculateMacd(List<Candle> candles, int index) {
        double shortEma = calculateEma(candles, index, SHORT_PERIOD);
        double longEma = calculateEma(candles, index, LONG_PERIOD);
        return shortEma - longEma;
    }

    private double calculateSignalLine(List<Candle> candles, int index) {
        // Signal line is the EMA of the MACD values
        return calculateEmaOfMacd(candles, index, SIGNAL_PERIOD);
    }

    /**
     * Standard EMA Calculation for Price
     */
    private double calculateEma(List<Candle> candles, int index, int period) {
        if (index < period - 1)
            return 0.0;

        // Optimization: Only go back enough steps to converge (e.g. 250 candles)
        // or start from the beginning if data is short.
        int lookback = 250;
        int startIndex = Math.max(period - 1, index - lookback);

        // 1. Initialize with SMA at startIndex
        double ema = calculateSma(candles, startIndex, period);

        // 2. Iterate forward to index
        double multiplier = 2.0 / (period + 1);
        for (int i = startIndex + 1; i <= index; i++) {
            double price = candles.get(i).getClosePrice();
            ema = (price - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * EMA Calculation for MACD values (nested)
     */
    private double calculateEmaOfMacd(List<Candle> candles, int index, int period) {
        // To calculate Signal Line at `index`, we need a series of MACD values.
        // We start `lookback` steps ago.

        int lookback = 250;
        // The first valid MACD value roughly appears after LONG_PERIOD.
        // So we can't start before LONG_PERIOD.
        int minStartIndex = LONG_PERIOD + period - 1; // Need `period` macd values for initial SMA

        if (index < minStartIndex)
            return 0.0;

        int startIndex = Math.max(minStartIndex, index - lookback);

        // 1. Initialize with SMA of MACD at startIndex
        // SMA of MACD = Average of MACD values from (startIndex - period + 1) to
        // startIndex
        double sumMacd = 0.0;
        for (int i = startIndex - period + 1; i <= startIndex; i++) {
            sumMacd += calculateMacd(candles, i);
        }
        double ema = sumMacd / period;

        // 2. Iterate forward
        double multiplier = 2.0 / (period + 1);
        for (int i = startIndex + 1; i <= index; i++) {
            double macdVal = calculateMacd(candles, i);
            ema = (macdVal - ema) * multiplier + ema;
        }
        return ema;
    }

    private double calculateSma(List<Candle> candles, int index, int period) {
        double sum = 0.0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).getClosePrice();
        }
        return sum / period;
    }
}
