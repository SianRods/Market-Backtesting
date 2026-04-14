package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.QuoteSummary;
import com.rods.backtestingstrategies.service.MarketDataService;
import com.rods.backtestingstrategies.service.TickerSeederService;
import com.rods.backtestingstrategies.service.YahooFinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yahoofinance.Stock;
import yahoofinance.quotes.stock.StockQuote;
import yahoofinance.quotes.stock.StockStats;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@CrossOrigin
public class MarketController {

    private final YahooFinanceService yahooFinanceService;
    private final MarketDataService marketDataService;
    private final TickerSeederService tickerSeederService;

    public MarketController(YahooFinanceService yahooFinanceService,
                            MarketDataService marketDataService,
                            TickerSeederService tickerSeederService) {
        this.yahooFinanceService = yahooFinanceService;
        this.marketDataService = marketDataService;
        this.tickerSeederService = tickerSeederService;
    }

    /**
     * Fetch & cache candle data for a stock symbol.
     * Uses DB-first approach with Yahoo Finance sync.
     */
    @GetMapping("/stock/{symbol}")
    public ResponseEntity<List<Candle>> getDailyStockData(@PathVariable String symbol) {
        System.out.println("Request Received for: " + symbol);
        return ResponseEntity.ok(marketDataService.getCandles(symbol));
    }

    /**
     * Get real-time quote summary from Yahoo Finance.
     * Includes price, change, volume, 52W range, fundamentals.
     */
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<?> getQuote(@PathVariable String symbol) {
        try {
            Stock stock = yahooFinanceService.getStock(symbol);
            if (stock == null || stock.getQuote() == null) {
                return ResponseEntity.notFound().build();
            }

            StockQuote quote = stock.getQuote();
            StockStats stats = stock.getStats();

            QuoteSummary summary = QuoteSummary.builder()
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
                    .exchange(stock.getStockExchange())
                    .currency(stock.getCurrency())
                    .price(quote.getPrice())
                    .change(quote.getChange())
                    .changePercent(quote.getChangeInPercent())
                    .previousClose(quote.getPreviousClose())
                    .open(quote.getOpen())
                    .dayHigh(quote.getDayHigh())
                    .dayLow(quote.getDayLow())
                    .volume(quote.getVolume())
                    .avgVolume(quote.getAvgVolume())
                    .yearHigh(quote.getYearHigh())
                    .yearLow(quote.getYearLow())
                    .marketCap(stats != null ? stats.getMarketCap() : null)
                    .pe(stats != null ? stats.getPe() : null)
                    .eps(stats != null ? stats.getEps() : null)
                    .priceToBook(stats != null ? stats.getPriceBook() : null)
                    .bookValue(stats != null ? stats.getBookValuePerShare() : null)
                    .dividendYield(stock.getDividend() != null ?
                            stock.getDividend().getAnnualYieldPercent() : null)
                    .build();

            return ResponseEntity.ok(summary);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch quote: " + e.getMessage()));
        }
    }

    /**
     * Manually trigger a re-seed of the ticker database.
     */
    @PostMapping("/reseed-tickers")
    public ResponseEntity<Map<String, Object>> reseedTickers() {
        int count = tickerSeederService.reseedTickers();
        return ResponseEntity.ok(Map.of(
                "message", "Ticker database re-seeded successfully",
                "totalSymbols", count
        ));
    }
}
