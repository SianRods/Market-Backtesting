package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.StockSymbol;
import com.rods.backtestingstrategies.repository.CandleRepository;
import com.rods.backtestingstrategies.repository.StockSymbolRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class MarketDataService {

    private final YahooFinanceService yahooFinanceService;
    private final CandleRepository candleRepository;
    private final StockSymbolRepository symbolRepository;

    public MarketDataService(YahooFinanceService yahooFinanceService,
            CandleRepository candleRepository,
            StockSymbolRepository symbolRepository) {
        this.yahooFinanceService = yahooFinanceService;
        this.candleRepository = candleRepository;
        this.symbolRepository = symbolRepository;
    }

    /**
     * Sync daily candle data from Yahoo Finance into the database.
     * Only inserts candles for dates not yet stored.
     */
    public void syncDailyCandles(String symbol) {
        System.out.println("Sync Method Called for: " + symbol);

        try {
            // Fetch 5 years of historical data for comprehensive backtesting
            Calendar from = Calendar.getInstance();
            from.add(Calendar.YEAR, -5);
            Calendar to = Calendar.getInstance();

            List<HistoricalQuote> history = yahooFinanceService.getHistoricalData(symbol, from, to);

            if (history == null || history.isEmpty()) {
                System.err.println("No historical data returned for: " + symbol);
                return;
            }

            // Fetch existing candle dates for this symbol
            Set<LocalDate> existingDates = new HashSet<>(candleRepository.findExistingDates(symbol));

            List<Candle> newCandles = new ArrayList<>();

            for (HistoricalQuote quote : history) {
                if (quote.getDate() == null || quote.getClose() == null) {
                    continue;
                }

                LocalDate date = quote.getDate().getTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();

                // Skip if candle already exists
                if (existingDates.contains(date)) {
                    continue;
                }

                Candle candle = new Candle();
                candle.setSymbol(symbol.toUpperCase());
                candle.setDate(date);
                candle.setOpenPrice(toDouble(quote.getOpen()));
                candle.setHighPrice(toDouble(quote.getHigh()));
                candle.setLowPrice(toDouble(quote.getLow()));
                candle.setClosePrice(toDouble(quote.getClose()));
                candle.setVolume(quote.getVolume() != null ? quote.getVolume() : 0L);

                newCandles.add(candle);
            }

            // Bulk insert
            if (!newCandles.isEmpty()) {
                try {
                    candleRepository.saveAll(newCandles);
                    System.out.println("Inserted " + newCandles.size() + " new candles for " + symbol);
                } catch (DataIntegrityViolationException e) {
                    System.err.println("Duplicate candles skipped for: " + symbol);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to fetch data from Yahoo Finance for: " + symbol + " - " + e.getMessage());
        }
    }

    /**
     * Get candles for a symbol. Fetches from DB first, syncs from API if needed.
     */
    public List<Candle> getCandles(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        List<Candle> candles = candleRepository.findBySymbolOrderByDateAsc(upperSymbol);

        // CASE 1: No data in DB → sync
        if (candles.isEmpty()) {
            syncDailyCandles(upperSymbol);
            return candleRepository.findBySymbolOrderByDateAsc(upperSymbol);
        }

        // CASE 2: Data exists → check if stale (last candle > 1 day old)
        LocalDate latestDate = candles.getLast().getDate();
        if (LocalDate.now().isAfter(latestDate.plusDays(1))) {
            syncDailyCandles(upperSymbol);
            candles = candleRepository.findBySymbolOrderByDateAsc(upperSymbol);
        }

        return candles;
    }

    /**
     * Search stock symbols from the local pre-seeded database.
     * Supports fuzzy matching on symbol and company name.
     */
    public List<StockSymbol> searchSymbols(String query) {
        return symbolRepository.searchSymbols(query);
    }

    /**
     * Search symbols filtered by exchange
     */
    public List<StockSymbol> searchSymbolsByExchange(String query, String exchange) {
        return symbolRepository.searchSymbolsByExchange(query, exchange);
    }

    /**
     * Get all symbols for a specific exchange
     */
    public List<StockSymbol> getSymbolsByExchange(String exchange) {
        return symbolRepository.findByExchange(exchange);
    }

    /**
     * Safe BigDecimal to double conversion
     */
    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}
