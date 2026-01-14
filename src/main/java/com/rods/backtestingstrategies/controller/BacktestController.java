package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.BacktestResult;
import com.rods.backtestingstrategies.service.BacktestService;
import com.rods.backtestingstrategies.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@CrossOrigin
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * Run a backtest for a given symbol using the configured strategy
     */
    @PostMapping("/{symbol}")
    public ResponseEntity<BacktestResult> runBacktest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "SMA") StrategyType strategy,
            @RequestParam(defaultValue = "100000") double capital
    ) {
        BacktestResult result = backtestService.backtest(symbol, strategy, capital);
        return ResponseEntity.ok(result);
    }
}
