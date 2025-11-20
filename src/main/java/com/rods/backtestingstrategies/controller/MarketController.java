package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.service.AlphaVantageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final AlphaVantageService alphaVantageService;

    public MarketController(AlphaVantageService alphaVantageService) {
        this.alphaVantageService = alphaVantageService;
    }

    @GetMapping("/daily/{symbol}")
    public ResponseEntity<?> getDaily(@PathVariable String symbol) {
        return ResponseEntity.ok(alphaVantageService.getDailySeries(symbol));
    }
}
