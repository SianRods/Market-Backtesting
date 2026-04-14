package com.rods.backtestingstrategies.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rods.backtestingstrategies.entity.StockSymbol;
import com.rods.backtestingstrategies.repository.StockSymbolRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to populate the stock_symbols table on startup
 * from a curated JSON file of global exchange tickers.
 *
 * Covers: NASDAQ, NYSE/S&P 500, NSE (India), BSE (India), LSE (UK), TSE (Japan).
 * Runs only when tickers are missing from the DB.
 */
@Service
public class TickerSeederService {

    private final StockSymbolRepository symbolRepository;
    private final ObjectMapper objectMapper;

    @Value("${ticker.seeder.enabled:true}")
    private boolean seederEnabled;

    public TickerSeederService(StockSymbolRepository symbolRepository) {
        this.symbolRepository = symbolRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void seedTickers() {
        if (!seederEnabled) {
            System.out.println("Ticker seeder is disabled.");
            return;
        }

        long existingCount = symbolRepository.count();
        if (existingCount > 0) {
            System.out.println("Ticker database already populated with " + existingCount + " symbols. Skipping seed.");
            return;
        }

        System.out.println("Seeding ticker database from ticker_data.json...");

        try {
            ClassPathResource resource = new ClassPathResource("ticker_data.json");
            InputStream inputStream = resource.getInputStream();
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode exchanges = root.get("exchanges");

            List<StockSymbol> allSymbols = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode exchange : exchanges) {
                String exchangeName = exchange.get("name").asText();
                String region = exchange.get("region").asText();
                String currency = exchange.get("currency").asText();
                String timezone = exchange.get("timezone").asText();
                String marketOpen = exchange.get("marketOpen").asText();
                String marketClose = exchange.get("marketClose").asText();

                JsonNode tickers = exchange.get("tickers");
                for (JsonNode ticker : tickers) {
                    StockSymbol symbol = new StockSymbol();
                    symbol.setSymbol(ticker.get("symbol").asText());
                    symbol.setName(ticker.get("name").asText());
                    symbol.setType("Equity");
                    symbol.setExchange(exchangeName);
                    symbol.setRegion(region);
                    symbol.setCurrency(currency);
                    symbol.setTimezone(timezone);
                    symbol.setMarketOpen(marketOpen);
                    symbol.setMarketClose(marketClose);
                    symbol.setSector(ticker.has("sector") ? ticker.get("sector").asText() : null);
                    symbol.setIndustry(ticker.has("industry") ? ticker.get("industry").asText() : null);
                    symbol.setMatchScore(1.0);
                    symbol.setLastFetched(now);
                    symbol.setSource("TICKER_SEED");

                    allSymbols.add(symbol);
                }
            }

            symbolRepository.saveAll(allSymbols);
            System.out.println("Successfully seeded " + allSymbols.size() + " tickers across " +
                    exchanges.size() + " exchanges.");

        } catch (IOException e) {
            System.err.println("Failed to seed ticker data: " + e.getMessage());
        }
    }

    /**
     * Manual re-seed: clears all existing symbols and re-imports from JSON.
     * Use this endpoint for manual updates.
     */
    public int reseedTickers() {
        symbolRepository.deleteAll();
        seedTickers();
        return (int) symbolRepository.count();
    }
}
