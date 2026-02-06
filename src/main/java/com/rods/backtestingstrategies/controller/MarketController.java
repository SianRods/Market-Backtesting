package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.Stock;
import com.rods.backtestingstrategies.repository.CandleRepository;
import com.rods.backtestingstrategies.service.AlphaVantageService;
import com.rods.backtestingstrategies.service.MarketDataService;
import com.rods.backtestingstrategies.service.YahooFinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@CrossOrigin
public class MarketController {

    @Autowired
    private final AlphaVantageService alphaVantageService;
    private final YahooFinanceService yahooFinanceService;
    private final CandleRepository candleRepository;
    private final MarketDataService marketDataService;

    public MarketController(AlphaVantageService alphaVantageService, YahooFinanceService yahooFinanceService,
            CandleRepository candleRepository, MarketDataService marketDataService) {
        this.alphaVantageService = alphaVantageService;
        this.yahooFinanceService = yahooFinanceService;
        this.candleRepository = candleRepository;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/daily/{symbol}")
    public ResponseEntity<?> getDaily(@PathVariable String symbol) {
        return ResponseEntity.ok(yahooFinanceService.getDailyStockData(symbol));
    }

    // Route for searching for company symbols using the api
    @GetMapping("/search/{symbol}")
    public ResponseEntity<?> getSymbols(@PathVariable String symbol) {
        return ResponseEntity.ok(alphaVantageService.getSymbols(symbol));
    }

    @GetMapping("/stocks")
    public ResponseEntity<?> getStocks() {
        List<Stock> stocks = new ArrayList<>();
        stocks.add(new Stock("AAPL", "Apple Corporation"));
        stocks.add(new Stock("MSFT", "Microsoft Corporation"));

        // Making it dynamic and

        return ResponseEntity.ok(stocks);

    }

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<List<Candle>> getDailyStockData(@PathVariable String symbol) {
        System.out.println("Request Received");
        System.out.println(symbol);
        return ResponseEntity.ok(marketDataService.getCandles(symbol));
    }

    // Endpoint for displaying the stock data with daily data
    // One Simple Idea is to use Database to cache the already available stock
    // symbols and them according fetch them
    // first from the DB if not available we can then hit the database

}
