package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.*;
import com.rods.backtestingstrategies.strategy.Strategy;
import com.rods.backtestingstrategies.strategy.StrategyFactory;
import com.rods.backtestingstrategies.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BacktestService {

    private final MarketDataService marketDataService;
    private final StrategyFactory strategyFactory;

    public BacktestResult backtest(
            String symbol,
            StrategyType strategyType,
            double initialCapital
    ) {

        Strategy strategy = strategyFactory.getStrategy(strategyType);

        List<Candle> candles = marketDataService.getCandles(symbol);

        // Safeguard: no data
        if (candles == null || candles.isEmpty()) {
            return BacktestResult.empty(initialCapital);
        }

        // Ensure chronological order
        candles.sort(Comparator.comparing(Candle::getDate));

        double cash = initialCapital;
        long shares = 0L;

        List<EquityPoint> equityCurve = new ArrayList<>();
        List<Transaction> transactions = new ArrayList<>();
        List<CrossOver> crossovers = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {

            Candle candle = candles.get(i);
            double price = candle.getClosePrice();

            TradeSignal signal = strategy.evaluate(candles, i)
                    .withStrategyName(strategy.getName());

            switch (signal.getSignalType()) {

                case BUY -> {
                    if (cash > 0 && shares == 0) {
                        long buyShares = (long) (cash / price);
                        if (buyShares > 0) {
                            cash -= buyShares * price;
                            shares += buyShares;

                            transactions.add(
                                    Transaction.buy(candle, price, buyShares, cash, cash + shares * price)
                            );
                            crossovers.add(CrossOver.bullish(candle));
                        }
                    }
                }

                case SELL -> {
                    if (shares > 0) {
                        double proceeds = shares * price;
                        cash += proceeds;

                        transactions.add(
                                Transaction.sell(candle, price, shares, cash, cash)
                        );
                        crossovers.add(CrossOver.bearish(candle));

                        shares = 0;
                    }
                }

                case HOLD -> {
                    // Explicit no-op
                }
            }

            double equity = cash + shares * price;
            equityCurve.add(
                    EquityPoint.of(candle, equity, shares, cash)
            );
        }

        EquityPoint last = equityCurve.get(equityCurve.size() - 1);
        double finalValue = last.getEquity();
        double pnl = finalValue - initialCapital;
        double returnPct = initialCapital == 0 ? 0 : (pnl / initialCapital) * 100.0;

        return BacktestResult.builder()
                .startCapital(initialCapital)
                .finalCapital(finalValue)
                .profitLoss(pnl)
                .returnPct(returnPct)
                .equityCurve(equityCurve)
                .transactions(transactions)
                .crossovers(crossovers)
                .build();
    }
}
