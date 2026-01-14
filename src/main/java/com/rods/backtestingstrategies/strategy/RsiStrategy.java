package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.TradeSignal;
import com.rods.backtestingstrategies.entity.SignalType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RsiStrategy implements Strategy {

    private static final int PERIOD = 14;
    private static final double OVERSOLD = 30.0;
    private static final double OVERBOUGHT = 70.0;

    @Override
    public TradeSignal evaluate(List<Candle> candles, int index) {

        // Not enough data
        if (index < PERIOD) {
            return TradeSignal.hold();
        }

        double rsi = calculateRsi(candles, index);

        Candle candle = candles.get(index);

        if (rsi < OVERSOLD) {
            return TradeSignal.buy(candle);
        }

        if (rsi > OVERBOUGHT) {
            return TradeSignal.sell(candle);
        }

        return TradeSignal.hold();
    }

    @Override
    public StrategyType getType() {
        return StrategyType.RSI;
    }

    @Override
    public String getName() {
        return "RSI Mean Reversion (14)";
    }

    /* ==========================
       RSI Calculation
       ========================== */

    private double calculateRsi(List<Candle> candles, int index) {

        double gain = 0.0;
        double loss = 0.0;

        for (int i = index - PERIOD + 1; i <= index; i++) {
            double change =
                    candles.get(i).getClosePrice()
                            - candles.get(i - 1).getClosePrice();

            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        if (loss == 0) {
            return 100.0;
        }

        double rs = gain / loss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
