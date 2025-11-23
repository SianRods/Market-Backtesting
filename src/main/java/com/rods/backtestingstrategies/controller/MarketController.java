package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.Stock;
import com.rods.backtestingstrategies.service.AlphaVantageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@CrossOrigin
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

    @GetMapping("/stocks")
    public ResponseEntity<?> getStocks(){
        List<Stock> stocks= new ArrayList<>();
        stocks.add( new Stock("AAPL","Apple Corporation"));
        stocks.add(new Stock("MSFT","Mircosoft Corportation"));
        return ResponseEntity.ok(stocks);

    }

}
