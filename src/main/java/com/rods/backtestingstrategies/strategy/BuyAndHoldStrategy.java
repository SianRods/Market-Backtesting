package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.SignalType;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BuyAndHoldStrategy implements Strategy {

    @Override
    public TradeSignal evaluate(List<Candle> candles, int index) {

        Candle candle = candles.get(index);

        // BUY on first candle
        if (index == 0) {
            return TradeSignal.buy(candle);
        }

        // SELL on last candle
        if (index == candles.size() - 1) {
            return TradeSignal.sell(candle);
        }

        // Otherwise HOLD
        return TradeSignal.hold();
    }

    @Override
    public StrategyType getType() {
        return StrategyType.BUY_AND_HOLD;
    }

    @Override
    public String getName() {
        return "Buy & Hold";
    }
}
