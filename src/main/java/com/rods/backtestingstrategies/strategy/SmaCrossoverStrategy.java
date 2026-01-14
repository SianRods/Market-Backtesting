package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SmaCrossoverStrategy implements Strategy {

    private final int shortPeriod;
    private final int longPeriod;

    public SmaCrossoverStrategy() {
        // Default values (can later be injected / configured)
        this.shortPeriod = 20;
        this.longPeriod = 50;
    }

    public SmaCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be less than long period");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public TradeSignal evaluate(List<Candle> candles, int index) {

        Candle candle = candles.get(index);

        // Not enough data to evaluate → HOLD with candle context
        if (index < longPeriod) {
            return TradeSignal.hold();
        }

        double prevShortSma = sma(candles, index - 1, shortPeriod);
        double prevLongSma = sma(candles, index - 1, longPeriod);

        double currShortSma = sma(candles, index, shortPeriod);
        double currLongSma = sma(candles, index, longPeriod);

        // Cross up → BUY
        if (prevShortSma <= prevLongSma && currShortSma > currLongSma) {
            return TradeSignal.buy(candle);
        }

        // Cross down → SELL
        if (prevShortSma >= prevLongSma && currShortSma < currLongSma) {
            return TradeSignal.sell(candle);
        }

        // No crossover → HOLD
        return TradeSignal.hold();
    }



    /**
     * Simple Moving Average at a specific index
     */
    private double sma(List<Candle> candles, int index, int period) {
        double sum = 0.0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).getClosePrice();
        }
        return sum / period;
    }



    @Override
    public String getName() {
        return "SMA Crossover (" + shortPeriod + ", " + longPeriod + ")";
    }


    @Override
    public StrategyType getType() {
        return StrategyType.SMA;
    }


}
