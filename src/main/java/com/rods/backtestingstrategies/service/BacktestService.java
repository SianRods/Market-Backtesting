package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.domain.BacktestEngine;
import com.rods.backtestingstrategies.domain.BacktestResult;
import com.rods.backtestingstrategies.domain.BuyAndHoldConfig;
import com.rods.backtestingstrategies.domain.DomainValidationException;
import com.rods.backtestingstrategies.domain.ExecutionModel;
import com.rods.backtestingstrategies.domain.MacdConfig;
import com.rods.backtestingstrategies.domain.RsiConfig;
import com.rods.backtestingstrategies.domain.SmaConfig;
import com.rods.backtestingstrategies.domain.StrategyComparisonResult;
import com.rods.backtestingstrategies.domain.StrategyConfig;
import com.rods.backtestingstrategies.entity.PortfolioRequest;
import com.rods.backtestingstrategies.entity.PortfolioResult;
import com.rods.backtestingstrategies.marketdata.MarketDataProviderException;
import com.rods.backtestingstrategies.marketdata.MarketDataSnapshot;
import com.rods.backtestingstrategies.marketdata.ProviderFailureType;
import com.rods.backtestingstrategies.strategy.StrategyType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spring adapter around the framework-independent Phase 1 domain engine. */
@Service
public class BacktestService {

    private final MarketDataService marketDataService;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public BacktestResult backtest(String symbol, StrategyType strategyType, double initialCapital) {
        return run(symbol, defaultConfiguration(strategyType), BigDecimal.valueOf(initialCapital), 0);
    }

    public BacktestResult backtestWithParams(
            String symbol, StrategyType strategyType, double initialCapital, Map<String, String> parameters) {
        return run(symbol, configuration(strategyType, parameters), BigDecimal.valueOf(initialCapital), 0);
    }

    public StrategyComparisonResult compareStrategies(String symbol, double initialCapital) {
        List<com.rods.backtestingstrategies.domain.Candle> candles = domainCandles(symbol);
        Map<com.rods.backtestingstrategies.domain.StrategyType, StrategyConfig> configurations = new LinkedHashMap<>();
        for (StrategyType type : StrategyType.values()) {
            StrategyConfig configuration = defaultConfiguration(type);
            configurations.put(configuration.type(), configuration);
        }
        int measurementStart = configurations.values().stream().mapToInt(StrategyConfig::warmupCandles).max().orElse(0);
        Map<com.rods.backtestingstrategies.domain.StrategyType, BacktestResult> results = new LinkedHashMap<>();
        for (StrategyConfig configuration : configurations.values()) {
            results.put(configuration.type(), engine.run(candles, configuration, BigDecimal.valueOf(initialCapital),
                    ExecutionModel.defaults(), measurementStart));
        }
        List<com.rods.backtestingstrategies.domain.StrategyType> rankByReturn = results.entrySet().stream()
                .sorted(Map.Entry.<com.rods.backtestingstrategies.domain.StrategyType, BacktestResult>comparingByValue(
                                Comparator.comparing(BacktestResult::returnPercent)).reversed())
                .map(Map.Entry::getKey)
                .toList();
        List<com.rods.backtestingstrategies.domain.StrategyType> rankBySharpe = results.entrySet().stream()
                .sorted((first, second) -> compareNullable(second.getValue().metrics().sharpeRatio(), first.getValue().metrics().sharpeRatio()))
                .map(Map.Entry::getKey)
                .toList();
        return new StrategyComparisonResult(symbol, BigDecimal.valueOf(initialCapital), results, rankByReturn,
                rankBySharpe, rankByReturn.getFirst(), measurementStart);
    }

    /**
     * Compatibility adapter for the pre-Phase-3 portfolio endpoint. Full multi-currency portfolio
     * accounting, synchronized equity curves, and aggregate metrics belong to Phase 5.
     */
    public PortfolioResult backtestPortfolio(PortfolioRequest request) {
        if (request == null || request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new DomainValidationException("portfolio entries are required");
        }
        if (request.getTotalCapital() <= 0) {
            throw new DomainValidationException("totalCapital must be positive");
        }
        StrategyType strategy = StrategyType.valueOf(request.getStrategy().toUpperCase());
        Map<String, BacktestResult> results = new LinkedHashMap<>();
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        BigDecimal totalCapital = BigDecimal.valueOf(request.getTotalCapital());
        BigDecimal finalCapital = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (PortfolioRequest.PortfolioEntry entry : request.getEntries()) {
            if (entry.getSymbol() == null || entry.getSymbol().isBlank() || entry.getWeight() <= 0) {
                throw new DomainValidationException("portfolio symbols and weights must be positive");
            }
            BigDecimal allocation = totalCapital.multiply(BigDecimal.valueOf(entry.getWeight()));
            totalWeight = totalWeight.add(BigDecimal.valueOf(entry.getWeight()));
            BacktestResult result = run(entry.getSymbol(), defaultConfiguration(strategy), allocation, 0);
            allocations.put(entry.getSymbol(), allocation);
            results.put(entry.getSymbol(), result);
            finalCapital = finalCapital.add(result.finalCapital());
        }
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw new DomainValidationException("portfolio weights must sum to exactly 1.0");
        }
        BigDecimal pnl = finalCapital.subtract(totalCapital);
        return new PortfolioResult(totalCapital, finalCapital, pnl,
                com.rods.backtestingstrategies.domain.Decimal.percentage(
                        com.rods.backtestingstrategies.domain.Decimal.divide(pnl, totalCapital)),
                strategy.name(), results, allocations,
                List.of("Portfolio-level synchronized metrics and FX accounting are introduced in Phase 5."));
    }

    private BacktestResult run(String symbol, StrategyConfig config, BigDecimal capital, int measurementStartIndex) {
        return engine.run(domainCandles(symbol), config, capital, ExecutionModel.defaults(), measurementStartIndex);
    }

    private List<com.rods.backtestingstrategies.domain.Candle> domainCandles(String symbol) {
        MarketDataSnapshot snapshot = marketDataService.getCandleSnapshot(symbol);
        if (!snapshot.isFresh()) {
            throw new MarketDataProviderException(ProviderFailureType.UNAVAILABLE_DATA, "MARKET_DATA_STORE",
                    "Backtest rejected because candle data is " + snapshot.freshness().status());
        }
        return snapshot.candles();
    }

    private StrategyConfig defaultConfiguration(StrategyType type) {
        return switch (type) {
            case SMA -> new SmaConfig(20, 50);
            case RSI -> RsiConfig.standard();
            case MACD -> MacdConfig.standard();
            case BUY_AND_HOLD -> new BuyAndHoldConfig();
        };
    }

    private StrategyConfig configuration(StrategyType type, Map<String, String> parameters) {
        try {
            return switch (type) {
                case SMA -> new SmaConfig(integer(parameters, "shortPeriod", 20), integer(parameters, "longPeriod", 50));
                case RSI -> new RsiConfig(integer(parameters, "rsiPeriod", 14), decimal(parameters, "oversold", "30"),
                        decimal(parameters, "overbought", "70"));
                case MACD -> new MacdConfig(integer(parameters, "fastPeriod", 12), integer(parameters, "slowPeriod", 26),
                        integer(parameters, "signalPeriod", 9));
                case BUY_AND_HOLD -> new BuyAndHoldConfig();
            };
        } catch (NumberFormatException exception) {
            throw new DomainValidationException("strategy parameters must be valid numeric values");
        }
    }

    private int integer(Map<String, String> parameters, String key, int defaultValue) {
        return Integer.parseInt(parameters.getOrDefault(key, String.valueOf(defaultValue)));
    }

    private BigDecimal decimal(Map<String, String> parameters, String key, String defaultValue) {
        return new BigDecimal(parameters.getOrDefault(key, defaultValue));
    }

    private int compareNullable(BigDecimal first, BigDecimal second) {
        if (first == null && second == null) return 0;
        if (first == null) return -1;
        if (second == null) return 1;
        return first.compareTo(second);
    }
}
