package com.rods.backtestingstrategies.service;


import com.rods.backtestingstrategies.entity.BacktestResult;
import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.SignalType;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BacktestService {

    @Autowired
    private final MarketDataService marketDataService;
    private final SmaCrossoverStrategy strategy;

    public BacktestService(MarketDataService marketDataService,
                           SmaCrossoverStrategy strategy) {
        this.marketDataService = marketDataService;
        this.strategy = strategy;
    }

    public BacktestResult backtest(String symbol, double initialCapital) {
        List<Candle> candles = marketDataService.getCandles(symbol);
        List<TradeSignal> signals = strategy.generateSignals(candles);

        double cash = initialCapital;
        double position = 0.0; // number of shares

        for (TradeSignal signal : signals) {
            double price = signal.getPrice();

            if (signal.getType() == SignalType.BUY && cash > 0) {
                position = cash / price;
                cash = 0;
            } else if (signal.getType() == SignalType.SELL && position > 0) {
                cash = position * price;
                position = 0;
            }
        }

        // Final portfolio value: mark to market at last close
        Candle last = candles.get(candles.size() - 1);
        double finalValue = cash + position * last.getClosePrice();
        double pnl = finalValue - initialCapital;

        return new BacktestResult(initialCapital, finalValue, pnl);
    }
}


