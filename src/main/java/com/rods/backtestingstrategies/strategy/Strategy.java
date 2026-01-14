package com.rods.backtestingstrategies.strategy;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.TradeSignal;

import java.util.List;

public interface Strategy {

    /**
     * Evaluate market state at a given candle index
     * and return a trading signal.
     *
     * @param candles ordered historical candles
     * @param index   current candle index (time step)
     * @return TradeSignal BUY / SELL / HOLD
     */

    TradeSignal evaluate(List<Candle> candles, int index);

    /**
     * Human-readable name of the strategy
     */
    String getName();

    //  Type of strategy being implemented
    StrategyType getType();

}
