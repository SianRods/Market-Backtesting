package com.rods.backtestingstrategies.service;

// the main task of this class is to be able to process the data from the alpha vantage endpoints
// and then use the data for saving the candles in the database
// Also note that we are always using the cross-over moving strategies with respect days

import com.crazzyghost.alphavantage.search.response.Match;
import com.crazzyghost.alphavantage.search.response.SearchResponse;
import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.Stock;
import com.rods.backtestingstrategies.entity.StockSymbol;
import com.rods.backtestingstrategies.repository.CandleRepository;

import com.rods.backtestingstrategies.repository.StockSymbolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MarketDataService {
    private static final int SYMBOL_CACHE_DAYS = 7;

    @Autowired
    private final AlphaVantageService alphaVantageService;
    private final YahooFinanceService yahooFinanceService;
    private final CandleRepository candleRepository;
    private final StockSymbolRepository symbolRepository;

    public MarketDataService(AlphaVantageService alphaVantageService, YahooFinanceService yahooFinanceService,
            CandleRepository candleRepository, StockSymbolRepository symbolRepository) {
        this.alphaVantageService = alphaVantageService;
        this.yahooFinanceService = yahooFinanceService;
        this.candleRepository = candleRepository;
        // this.stockSymbolRepo = stockSymbolRepo;
        this.symbolRepository = symbolRepository;
    }

    public void syncDailyCandles(String symbol) {

        System.out.println("Sync Method Called for the given Request ");

        yahoofinance.Stock stock = yahooFinanceService.getDailyStockData(symbol);

        if (stock == null) {
            System.err.println("Could not fetch data from Yahoo Finance for symbol: " + symbol);
            return;
        }

        List<HistoricalQuote> history;
        try {
            history = stock.getHistory();
        } catch (IOException e) {
            System.err.println("Error fetching history for symbol: " + symbol);
            e.printStackTrace();
            return;
        }

        // Fetch existing candle dates for this symbol
        Set<LocalDate> existingDates = new HashSet<>(candleRepository.findExistingDates(symbol));

        List<Candle> newCandles = new ArrayList<>();

        for (HistoricalQuote quote : history) {

            // Convert Calendar to LocalDate
            LocalDate date = quote.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            // Skip if candle already exists
            if (existingDates.contains(date)) {
                continue;
            }

            Candle candle = new Candle();
            candle.setSymbol(symbol);
            candle.setDate(date);
            candle.setOpenPrice(quote.getOpen() != null ? quote.getOpen().doubleValue() : 0.0);
            candle.setHighPrice(quote.getHigh() != null ? quote.getHigh().doubleValue() : 0.0);
            candle.setLowPrice(quote.getLow() != null ? quote.getLow().doubleValue() : 0.0);
            candle.setClosePrice(quote.getClose() != null ? quote.getClose().doubleValue() : 0.0);
            candle.setVolume(quote.getVolume() != null ? quote.getVolume() : 0L);

            newCandles.add(candle);
        }

        // Bulk insert (much faster)
        if (!newCandles.isEmpty()) {
            try {
                candleRepository.saveAll(newCandles);
            } catch (DataIntegrityViolationException e) {
                // Duplicate candle attempted → safely ignore or log
                System.err.println("Duplicate Candles insertion is not valid ");
            }
        }
    }

    // Used for rendering the charts on the frontend for every stocks
    public List<Candle> getCandles(String symbol) {

        List<Candle> candles = candleRepository.findBySymbolOrderByDateAsc(symbol);

        // CASE 1: No data in DB → sync
        if (candles.isEmpty()) {
            syncDailyCandles(symbol);
            return candleRepository.findBySymbolOrderByDateAsc(symbol);
        }

        // CASE 2: Data exists → check latest candle
        LocalDate latestDate = candles.getLast().getDate();

        if (LocalDate.now().isAfter(latestDate)) {
            syncDailyCandles(symbol);
            candles = candleRepository.findBySymbolOrderByDateAsc(symbol);
        }

        return candles;
    }

    public List<StockSymbol> searchSymbols(String query) {

        // Search DB first
        List<StockSymbol> dbResults = symbolRepository.searchSymbols(query);

        // Check freshness
        LocalDateTime lastFetched = symbolRepository.findLastFetchedForQuery(query);

        boolean shouldRefresh = dbResults.isEmpty()
                || lastFetched == null
                || lastFetched.isBefore(LocalDateTime.now().minusDays(SYMBOL_CACHE_DAYS));

        if (!shouldRefresh) {
            System.out.println("Fetching key from DB: " + query);
            return dbResults;
        }

        System.out.println("Fetching key from API: " + query);

        // Call AlphaVantage only if needed
        SearchResponse response = alphaVantageService.getSymbols(query);

        if (response == null || response.getBestMatches() == null) {
            return dbResults;
        }

        List<StockSymbol> newSymbols = new ArrayList<>();

        for (Match match : response.getBestMatches()) {

            // Prevent duplicates
            if (symbolRepository.findBySymbol(match.getSymbol()) != null) {
                continue;
            }

            StockSymbol symbol = new StockSymbol();
            symbol.setSymbol(match.getSymbol());
            symbol.setName(match.getName());
            symbol.setType(match.getType());
            symbol.setRegion(match.getRegion());
            symbol.setMarketOpen(match.getMarketOpen());
            symbol.setMarketClose(match.getMarketClose());
            symbol.setTimezone(match.getTimezone());
            symbol.setCurrency(match.getCurrency());
            symbol.setMatchScore(Double.parseDouble(match.getMatchScore()));
            symbol.setLastFetched(LocalDateTime.now());

            newSymbols.add(symbol);
        }

        if (!newSymbols.isEmpty()) {
            symbolRepository.saveAll(newSymbols);
        }

        // Return updated results
        return symbolRepository.searchSymbols(query);
    }

}
