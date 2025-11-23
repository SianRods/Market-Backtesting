package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Data
@AllArgsConstructor
public class BacktestService {

    @Autowired
    private final MarketDataService marketDataService;
    private final SmaCrossoverStrategy smaCrossoverStrategy;


    public BacktestResult backtest(String symbol, String strategyName, double initialCapital,Integer shortDuration, Integer longDuration) {
        List<Candle> candles = marketDataService.getCandles(symbol);

        // safe-guard: no data -> return a minimal result with empty lists
        if (candles == null || candles.isEmpty()) {
            System.err.println("WARNING: No data found for " + symbol + ". Check your API Key or limits.");
            return new BacktestResult(
                    initialCapital,        // start
                    initialCapital,        // final
                    0.0,                   // pnl
                    0.0,                   // returnPct
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        // Ensure candles are sorted ascending by date
        candles.sort(Comparator.comparing(Candle::getDate));

        // generate signals (assumed chronological)
        List<TradeSignal> signals = smaCrossoverStrategy.generateSignals(candles,shortDuration,longDuration);
        // Map signals by date string for quick lookup (supports multiple signals on same date)
        Map<String, List<TradeSignal>> signalsByDate = new HashMap<>();
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;

        for (TradeSignal s : signals) {
            // assume TradeSignal.getDate() returns java.time.LocalDate or String
            String d;
            Object sd = s.getDate();
            if (sd instanceof java.time.LocalDate) {
                d = ((java.time.LocalDate) sd).format(iso);
            } else {
                d = String.valueOf(sd);
            }
            signalsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(s);
        }

        double cash = initialCapital;
        long shares = 0L; // integer shares for all-in; change to double for fractional shares
        List<EquityPoint> equityCurve = new ArrayList<>();
        List<Transaction> transactions = new ArrayList<>();
        List<CrossOver> crossovers = new ArrayList<>();

        // Walk through each candle (daily) and execute signals when present
        for (Candle c : candles) {
            String dateStr;
            Object cd = c.getDate();
            if (cd instanceof java.time.LocalDate) dateStr = ((java.time.LocalDate) cd).format(iso);
            else dateStr = String.valueOf(cd);

            double price = c.getClosePrice();

            // if there are signals on this date, process them in order
            List<TradeSignal> daySignals = signalsByDate.getOrDefault(dateStr, Collections.emptyList());

            // process each signal for that day
            for (TradeSignal signal : daySignals) {
                SignalType type = signal.getType();
                double sigPrice = signal.getPrice() > 0 ? signal.getPrice() : price; // fallback to candle price
                // record crossover event for UI (bull/bear)
                crossovers.add(new CrossOver(dateStr, type == SignalType.BUY ? "bull" : "bear", sigPrice));

                if (type == SignalType.BUY && cash > 0.0) {
                    // ALL-IN buy (integer shares)
                    long buyShares = (long) Math.floor(cash / sigPrice);
                    if (buyShares > 0) {
                        double spent = buyShares * sigPrice;
                        shares += buyShares;
                        cash -= spent;

                        double equityAfter = cash + shares * price;
                        transactions.add(new Transaction(dateStr, "BUY", sigPrice, buyShares, cash, equityAfter));
                    }
                    // if buyShares==0 => price > cash, can't buy any full share
                } else if (type == SignalType.SELL && shares > 0) {
                    // sell all shares
                    long sellShares = shares;
                    double proceeds = sellShares * sigPrice;
                    cash += proceeds;
                    shares = 0L;

                    double equityAfter = cash; // no shares left
                    transactions.add(new Transaction(dateStr, "SELL", sigPrice, sellShares, cash, equityAfter));
                }
                // else ignore redundant signals (e.g., BUY when already in position)
            }

            // compute equity for the day (cash + shares * price)
            double equity = cash + shares * price;
            equityCurve.add(new EquityPoint(dateStr, price, equity, shares, cash));
        }

        // final values
        EquityPoint lastPoint = equityCurve.get(equityCurve.size() - 1);
        double finalValue = lastPoint.getEquity();
        double pnl = finalValue - initialCapital;
        double returnPct = initialCapital != 0 ? (pnl / initialCapital) * 100.0 : 0.0;

        return new BacktestResult(initialCapital, finalValue, pnl, returnPct, equityCurve, transactions, crossovers);
    }




}
