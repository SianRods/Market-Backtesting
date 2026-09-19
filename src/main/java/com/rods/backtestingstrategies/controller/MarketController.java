package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.marketdata.MarketDataSnapshot;
import com.rods.backtestingstrategies.marketdata.MarketQuote;
import com.rods.backtestingstrategies.service.MarketDataService;
import com.rods.backtestingstrategies.service.TickerSeederService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transitional market-data endpoints. Phase 3 owns the versioned public error and DTO contract. */
@RestController
@RequestMapping("/api/market")
@Slf4j
public class MarketController {

    private final MarketDataService marketDataService;
    private final TickerSeederService tickerSeederService;

    public MarketController(MarketDataService marketDataService, TickerSeederService tickerSeederService) {
        this.marketDataService = marketDataService;
        this.tickerSeederService = tickerSeederService;
    }

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<MarketDataSnapshot> getDailyStockData(@PathVariable String symbol) {
        log.info("Daily market data requested for symbol={}", symbol);
        return ResponseEntity.ok(marketDataService.getCandleSnapshot(symbol));
    }

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<MarketQuote> getQuote(@PathVariable String symbol) {
        return ResponseEntity.ok(marketDataService.getQuote(symbol));
    }

    @PostMapping("/reseed-tickers")
    public ResponseEntity<Map<String, Object>> reseedTickers() {
        int count = tickerSeederService.reseedTickers();
        return ResponseEntity.ok(Map.of("message", "Ticker database re-seeded successfully", "totalSymbols", count));
    }
}
