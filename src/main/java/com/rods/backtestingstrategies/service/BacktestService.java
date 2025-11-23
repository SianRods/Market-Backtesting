package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.BacktestResult;
import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.SignalType;
import com.rods.backtestingstrategies.entity.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BacktestService {

    @Autowired
    private final MarketDataService marketDataService;
    private final SmaCrossoverStrategy smaCrossoverStrategy;


    public BacktestService(MarketDataService marketDataService, SmaCrossoverStrategy smaCrossoverStrategy) {
        this.marketDataService = marketDataService;
        this.smaCrossoverStrategy = smaCrossoverStrategy;
    }

    public BacktestResult backtest(String symbol, String strategyName, double initialCapital) {
        List<Candle> candles = marketDataService.getCandles(symbol);
        if (candles == null || candles.isEmpty()) {
            System.err.println("WARNING: No data found for " + symbol + ". Check your API Key or limits.");

            // Return a result with 0 profit instead of crashing
            return new BacktestResult(initialCapital, initialCapital, 0.0);
        }

        List<TradeSignal> signals = smaCrossoverStrategy.generateSignals(candles);

        double cash = initialCapital;
        double position = 0.0;

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
        // Final portfolio value
        // This line caused the crash because candles.size() was 0
        Candle last = candles.get(candles.size() - 1);

        double finalValue = cash + position * last.getClosePrice();
        double pnl = finalValue - initialCapital;

        return new BacktestResult(initialCapital, finalValue, pnl);
    }
}
