package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.service.AlphaVantageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    @Autowired
    private final AlphaVantageService alphaVantageService;

    public MarketController(AlphaVantageService alphaVantageService) {
        this.alphaVantageService = alphaVantageService;
    }

    @GetMapping("/daily/{symbol}")
    public ResponseEntity<?> getDaily(@PathVariable String symbol) {
        return ResponseEntity.ok(alphaVantageService.getDailySeries(symbol));
    }

    // Route for searching for company symbols using the api
    @GetMapping("/search/{symbol}")
    public ResponseEntity<?> getSymbols(@PathVariable String symbol) {
        return ResponseEntity.ok(alphaVantageService.getSymbols(symbol));
    }

}
