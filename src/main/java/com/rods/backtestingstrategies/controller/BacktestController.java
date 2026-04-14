package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.BacktestResult;
import com.rods.backtestingstrategies.entity.PortfolioRequest;
import com.rods.backtestingstrategies.entity.PortfolioResult;
import com.rods.backtestingstrategies.entity.StrategyComparisonResult;
import com.rods.backtestingstrategies.service.BacktestService;
import com.rods.backtestingstrategies.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
@CrossOrigin
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * Run a backtest for a given symbol using a strategy.
     * Supports optional custom strategy parameters.
     *
     * Examples:
     *   POST /api/backtest/AAPL?strategy=SMA&capital=100000
     *   POST /api/backtest/AAPL?strategy=SMA&capital=100000&shortPeriod=10&longPeriod=30
     *   POST /api/backtest/AAPL?strategy=MACD&fastPeriod=8&slowPeriod=21&signalPeriod=5
     */
    @PostMapping("/{symbol}")
    public ResponseEntity<BacktestResult> runBacktest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "SMA") StrategyType strategy,
            @RequestParam(defaultValue = "100000") double capital,
            @RequestParam(required = false) Integer shortPeriod,
            @RequestParam(required = false) Integer longPeriod,
            @RequestParam(required = false) Integer fastPeriod,
            @RequestParam(required = false) Integer slowPeriod,
            @RequestParam(required = false) Integer signalPeriod
    ) {
        // Check if any custom params were provided
        Map<String, String> params = new HashMap<>();
        if (shortPeriod != null) params.put("shortPeriod", String.valueOf(shortPeriod));
        if (longPeriod != null) params.put("longPeriod", String.valueOf(longPeriod));
        if (fastPeriod != null) params.put("fastPeriod", String.valueOf(fastPeriod));
        if (slowPeriod != null) params.put("slowPeriod", String.valueOf(slowPeriod));
        if (signalPeriod != null) params.put("signalPeriod", String.valueOf(signalPeriod));

        BacktestResult result;
        if (params.isEmpty()) {
            result = backtestService.backtest(symbol, strategy, capital);
        } else {
            result = backtestService.backtestWithParams(symbol, strategy, capital, params);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Compare ALL available strategies on the same stock data.
     * Returns rankings by return % and Sharpe Ratio.
     *
     * POST /api/backtest/compare/AAPL?capital=100000
     */
    @PostMapping("/compare/{symbol}")
    public ResponseEntity<StrategyComparisonResult> compareStrategies(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100000") double capital
    ) {
        StrategyComparisonResult result = backtestService.compareStrategies(symbol, capital);
        return ResponseEntity.ok(result);
    }

    /**
     * Run a portfolio-level backtest across multiple stocks.
     *
     * POST /api/backtest/portfolio
     * Body:
     * {
     *   "entries": [
     *     {"symbol": "AAPL", "weight": 0.4},
     *     {"symbol": "MSFT", "weight": 0.3},
     *     {"symbol": "GOOGL", "weight": 0.3}
     *   ],
     *   "totalCapital": 100000,
     *   "strategy": "SMA"
     * }
     */
    @PostMapping("/portfolio")
    public ResponseEntity<PortfolioResult> runPortfolioBacktest(
            @RequestBody PortfolioRequest request
    ) {
        PortfolioResult result = backtestService.backtestPortfolio(request);
        return ResponseEntity.ok(result);
    }
}
