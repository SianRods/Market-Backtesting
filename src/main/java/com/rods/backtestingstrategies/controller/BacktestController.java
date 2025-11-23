package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.BacktestResult;
import com.rods.backtestingstrategies.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

//Exposing the various strategies for backtesting through this controller
// -> for now have only implemented the SMA crossover strategy and will keep the file structure  same
// -> which
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    @Autowired
    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/{symbol}")
    public ResponseEntity<BacktestResult> runBacktest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "SMA Crossover") String strategy,
            @RequestParam(defaultValue = "100000") double capital) {
        BacktestResult result = backtestService.backtest(symbol, strategy, capital);
        return ResponseEntity.ok(result);
    }
}
