package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.*;
import com.rods.backtestingstrategies.strategy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BacktestService {

    private final MarketDataService marketDataService;
    private final StrategyFactory strategyFactory;

    /**
     * Run a backtest with default strategy parameters.
     */
    public BacktestResult backtest(
            String symbol,
            StrategyType strategyType,
            double initialCapital
    ) {
        Strategy strategy = strategyFactory.getStrategy(strategyType);
        return executeBacktest(symbol, strategy, initialCapital);
    }

    /**
     * Run a backtest with custom strategy parameters.
     */
    public BacktestResult backtestWithParams(
            String symbol,
            StrategyType strategyType,
            double initialCapital,
            Map<String, String> params
    ) {
        Strategy strategy = createParameterizedStrategy(strategyType, params);
        return executeBacktest(symbol, strategy, initialCapital);
    }

    /**
     * Compare all available strategies on the same stock data.
     */
    public StrategyComparisonResult compareStrategies(
            String symbol,
            double initialCapital
    ) {
        Map<String, BacktestResult> results = new LinkedHashMap<>();

        for (StrategyType type : StrategyType.values()) {
            try {
                Strategy strategy = strategyFactory.getStrategy(type);
                BacktestResult result = executeBacktest(symbol, strategy, initialCapital);
                results.put(strategy.getName(), result);
            } catch (IllegalArgumentException e) {
                // Strategy not implemented yet, skip
            }
        }

        // Rank by return %
        List<String> rankByReturn = results.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getReturnPct(), a.getValue().getReturnPct()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Rank by Sharpe Ratio
        List<String> rankBySharpe = results.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().getMetrics().getSharpeRatio(),
                        a.getValue().getMetrics().getSharpeRatio()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String best = rankByReturn.isEmpty() ? "N/A" : rankByReturn.getFirst();

        return StrategyComparisonResult.builder()
                .symbol(symbol)
                .initialCapital(initialCapital)
                .results(results)
                .rankByReturn(rankByReturn)
                .rankBySharpe(rankBySharpe)
                .bestStrategy(best)
                .build();
    }

    /**
     * Run portfolio-level backtest across multiple symbols.
     */
    public PortfolioResult backtestPortfolio(PortfolioRequest request) {
        StrategyType strategyType = StrategyType.valueOf(request.getStrategy().toUpperCase());
        Strategy strategy = strategyFactory.getStrategy(strategyType);

        Map<String, BacktestResult> symbolResults = new LinkedHashMap<>();
        Map<String, Double> allocations = new LinkedHashMap<>();

        double totalFinalValue = 0;

        for (PortfolioRequest.PortfolioEntry entry : request.getEntries()) {
            double allocatedCapital = request.getTotalCapital() * entry.getWeight();
            allocations.put(entry.getSymbol(), allocatedCapital);

            BacktestResult result = executeBacktest(entry.getSymbol(), strategy, allocatedCapital);
            symbolResults.put(entry.getSymbol(), result);
            totalFinalValue += result.getFinalCapital();
        }

        double totalPnL = totalFinalValue - request.getTotalCapital();
        double totalReturnPct = request.getTotalCapital() == 0 ? 0 :
                (totalPnL / request.getTotalCapital()) * 100.0;

        // Aggregate metrics weighted by allocation
        PerformanceMetrics aggregateMetrics = calculateAggregateMetrics(symbolResults, allocations, request.getTotalCapital());

        return PortfolioResult.builder()
                .totalCapital(request.getTotalCapital())
                .finalValue(totalFinalValue)
                .totalPnL(totalPnL)
                .totalReturnPct(totalReturnPct)
                .strategyUsed(strategy.getName())
                .aggregateMetrics(aggregateMetrics)
                .symbolResults(symbolResults)
                .allocations(allocations)
                .build();
    }

    /* ==========================
       Core Execution Engine
       ========================== */

    private BacktestResult executeBacktest(String symbol, Strategy strategy, double initialCapital) {
        List<Candle> candles = marketDataService.getCandles(symbol);

        if (candles == null || candles.isEmpty()) {
            return BacktestResult.empty(initialCapital);
        }

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
                case HOLD -> { /* no-op */ }
            }

            double equity = cash + shares * price;
            equityCurve.add(EquityPoint.of(candle, equity, shares, cash));
        }

        EquityPoint last = equityCurve.getLast();
        double finalValue = last.getEquity();
        double pnl = finalValue - initialCapital;
        double returnPct = initialCapital == 0 ? 0 : (pnl / initialCapital) * 100.0;

        // Calculate advanced metrics
        PerformanceMetrics metrics = calculateMetrics(equityCurve, transactions, initialCapital);

        return BacktestResult.builder()
                .startCapital(initialCapital)
                .finalCapital(finalValue)
                .profitLoss(pnl)
                .returnPct(returnPct)
                .strategyName(strategy.getName())
                .metrics(metrics)
                .equityCurve(equityCurve)
                .transactions(transactions)
                .crossovers(crossovers)
                .build();
    }

    /* ==========================
       Advanced Metrics Calculator
       ========================== */

    private PerformanceMetrics calculateMetrics(
            List<EquityPoint> equityCurve,
            List<Transaction> transactions,
            double initialCapital
    ) {
        if (equityCurve.isEmpty()) {
            return PerformanceMetrics.builder().build();
        }

        // --- Max Drawdown ---
        double maxDrawdown = calculateMaxDrawdown(equityCurve);

        // --- Trade Analysis ---
        List<Double> tradePnLs = calculateTradePnLs(transactions);
        int totalTrades = tradePnLs.size();
        int winningTrades = (int) tradePnLs.stream().filter(p -> p > 0).count();
        int losingTrades = (int) tradePnLs.stream().filter(p -> p < 0).count();
        double winRate = totalTrades == 0 ? 0 : (double) winningTrades / totalTrades * 100.0;

        double avgWin = tradePnLs.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = tradePnLs.stream().filter(p -> p < 0).mapToDouble(Double::doubleValue).average().orElse(0);
        double winLossRatio = avgLoss == 0 ? 0 : Math.abs(avgWin / avgLoss);

        double grossProfit = tradePnLs.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).sum();
        double grossLoss = Math.abs(tradePnLs.stream().filter(p -> p < 0).mapToDouble(Double::doubleValue).sum());
        double profitFactor = grossLoss == 0 ? 0 : grossProfit / grossLoss;

        // --- Sharpe Ratio ---
        double sharpeRatio = calculateSharpeRatio(equityCurve);

        // --- Annualized Return ---
        double finalEquity = equityCurve.getLast().getEquity();
        long days = ChronoUnit.DAYS.between(
                equityCurve.getFirst().getDate(),
                equityCurve.getLast().getDate()
        );
        double years = Math.max(days / 365.25, 0.01);
        double annualizedReturn = (Math.pow(finalEquity / initialCapital, 1.0 / years) - 1.0) * 100.0;

        // --- Average Holding Period ---
        double avgHoldingDays = calculateAvgHoldingPeriod(transactions);

        return PerformanceMetrics.builder()
                .sharpeRatio(round(sharpeRatio))
                .maxDrawdown(round(maxDrawdown))
                .winRate(round(winRate))
                .avgWin(round(avgWin))
                .avgLoss(round(avgLoss))
                .winLossRatio(round(winLossRatio))
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .annualizedReturn(round(annualizedReturn))
                .profitFactor(round(profitFactor))
                .avgHoldingPeriodDays(round(avgHoldingDays))
                .build();
    }

    private double calculateMaxDrawdown(List<EquityPoint> equityCurve) {
        double peak = equityCurve.getFirst().getEquity();
        double maxDrawdown = 0;

        for (EquityPoint point : equityCurve) {
            if (point.getEquity() > peak) {
                peak = point.getEquity();
            }
            double drawdown = (peak - point.getEquity()) / peak * 100.0;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        return -maxDrawdown; // Return as negative percentage
    }

    private double calculateSharpeRatio(List<EquityPoint> equityCurve) {
        if (equityCurve.size() < 2) return 0;

        // Daily returns
        List<Double> dailyReturns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prevEquity = equityCurve.get(i - 1).getEquity();
            double currEquity = equityCurve.get(i).getEquity();
            if (prevEquity != 0) {
                dailyReturns.add((currEquity - prevEquity) / prevEquity);
            }
        }

        if (dailyReturns.isEmpty()) return 0;

        double avgReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(
                dailyReturns.stream()
                        .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                        .average()
                        .orElse(0)
        );

        if (stdDev == 0) return 0;

        // Annualized Sharpe (assuming 252 trading days, risk-free rate = 0)
        return (avgReturn / stdDev) * Math.sqrt(252);
    }

    private List<Double> calculateTradePnLs(List<Transaction> transactions) {
        List<Double> pnls = new ArrayList<>();
        Double buyPrice = null;
        long buyShares = 0;

        for (Transaction tx : transactions) {
            if (tx.getType() == SignalType.BUY) {
                buyPrice = tx.getPrice();
                buyShares = tx.getShares();
            } else if (tx.getType() == SignalType.SELL && buyPrice != null) {
                double pnl = (tx.getPrice() - buyPrice) * buyShares;
                pnls.add(pnl);
                buyPrice = null;
                buyShares = 0;
            }
        }
        return pnls;
    }

    private double calculateAvgHoldingPeriod(List<Transaction> transactions) {
        List<Long> holdingDays = new ArrayList<>();
        Transaction buyTx = null;

        for (Transaction tx : transactions) {
            if (tx.getType() == SignalType.BUY) {
                buyTx = tx;
            } else if (tx.getType() == SignalType.SELL && buyTx != null) {
                long days = ChronoUnit.DAYS.between(buyTx.getDate(), tx.getDate());
                holdingDays.add(days);
                buyTx = null;
            }
        }

        return holdingDays.isEmpty() ? 0 :
                holdingDays.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private PerformanceMetrics calculateAggregateMetrics(
            Map<String, BacktestResult> symbolResults,
            Map<String, Double> allocations,
            double totalCapital
    ) {
        // Weighted average of individual metrics
        double weightedSharpe = 0;
        double weightedReturn = 0;
        double totalMaxDrawdown = 0;
        int totalTrades = 0;
        int totalWins = 0;
        int totalLosses = 0;

        for (var entry : symbolResults.entrySet()) {
            double weight = allocations.getOrDefault(entry.getKey(), 0.0) / totalCapital;
            PerformanceMetrics m = entry.getValue().getMetrics();

            weightedSharpe += m.getSharpeRatio() * weight;
            weightedReturn += m.getAnnualizedReturn() * weight;
            totalMaxDrawdown = Math.min(totalMaxDrawdown, m.getMaxDrawdown());
            totalTrades += m.getTotalTrades();
            totalWins += m.getWinningTrades();
            totalLosses += m.getLosingTrades();
        }

        double winRate = totalTrades == 0 ? 0 : (double) totalWins / totalTrades * 100.0;

        return PerformanceMetrics.builder()
                .sharpeRatio(round(weightedSharpe))
                .maxDrawdown(round(totalMaxDrawdown))
                .winRate(round(winRate))
                .totalTrades(totalTrades)
                .winningTrades(totalWins)
                .losingTrades(totalLosses)
                .annualizedReturn(round(weightedReturn))
                .build();
    }

    /* ==========================
       Parameterized Strategy Factory
       ========================== */

    private Strategy createParameterizedStrategy(StrategyType type, Map<String, String> params) {
        return switch (type) {
            case SMA -> {
                int shortPeriod = Integer.parseInt(params.getOrDefault("shortPeriod", "20"));
                int longPeriod = Integer.parseInt(params.getOrDefault("longPeriod", "50"));
                yield new SmaCrossoverStrategy(shortPeriod, longPeriod);
            }
            case RSI -> {
                // RSI uses class constants — for custom params, create a new configurable version
                yield strategyFactory.getStrategy(type);
            }
            case MACD -> {
                int fast = Integer.parseInt(params.getOrDefault("fastPeriod", "12"));
                int slow = Integer.parseInt(params.getOrDefault("slowPeriod", "26"));
                int signal = Integer.parseInt(params.getOrDefault("signalPeriod", "9"));
                yield new MacdStrategy(fast, slow, signal);
            }
            case BUY_AND_HOLD -> strategyFactory.getStrategy(type);
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
