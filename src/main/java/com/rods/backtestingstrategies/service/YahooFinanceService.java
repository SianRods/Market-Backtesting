package com.rods.backtestingstrategies.service;

import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;
import yahoofinance.quotes.stock.StockQuote;
import yahoofinance.quotes.stock.StockStats;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Service wrapping the Yahoo Finance API.
 * No API key required. No enforced rate limits.
 */
@Service
public class YahooFinanceService {

    static {
        // Set standard user-agent so Yahoo doesn't reject as bot with 429 Error
        System.setProperty("http.agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
    }

    /**
     * Fetch full stock info (quote, stats, dividend)
     */
    public Stock getStock(String symbol) throws IOException {
        return YahooFinance.get(symbol);
    }

    /**
     * Fetch stock with full historical data (default: 1 year, daily)
     */
    public Stock getStockWithHistory(String symbol) throws IOException {
        return YahooFinance.get(symbol, true);
    }

    /**
     * Fetch historical daily data for a custom date range using v8 API (Bypasses
     * 429)
     */
    public List<HistoricalQuote> getHistoricalData(String symbol, Calendar from, Calendar to) throws IOException {
        String urlString = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
                symbol, from.getTimeInMillis() / 1000, to.getTimeInMillis() / 1000);

        return fetchHistoryFromV8(symbol, urlString);
    }

    /**
     * Fetch historical daily data with default lookback (1 year)
     */
    public List<HistoricalQuote> getHistoricalData(String symbol) throws IOException {
        Calendar from = Calendar.getInstance();
        from.add(Calendar.YEAR, -1);
        Calendar to = Calendar.getInstance();
        return getHistoricalData(symbol, from, to);
    }

    private List<HistoricalQuote> fetchHistoryFromV8(String symbol, String urlString) throws IOException {
        java.util.List<HistoricalQuote> history = new java.util.ArrayList<>();
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection request = (java.net.HttpURLConnection) url.openConnection();
            request.setRequestMethod("GET");
            request.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            request.connect();

            int responseCode = request.getResponseCode();
            if (responseCode == 404)
                return history; // Stock not found

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(request.getInputStream());
            com.fasterxml.jackson.databind.JsonNode result = root.path("chart").path("result").get(0);

            if (result == null || result.isMissingNode())
                return history;

            com.fasterxml.jackson.databind.JsonNode timestampNode = result.path("timestamp");
            com.fasterxml.jackson.databind.JsonNode quoteNode = result.path("indicators").path("quote").get(0);
            com.fasterxml.jackson.databind.JsonNode adjCloseNode = result.path("indicators").path("adjclose").get(0);

            if (timestampNode.isMissingNode() || quoteNode.isMissingNode())
                return history;

            for (int i = 0; i < timestampNode.size(); i++) {
                long timestamp = timestampNode.get(i).asLong();
                java.util.Calendar date = java.util.Calendar.getInstance();
                date.setTimeInMillis(timestamp * 1000);

                java.math.BigDecimal open = getBigDecimal(quoteNode.path("open").get(i));
                java.math.BigDecimal high = getBigDecimal(quoteNode.path("high").get(i));
                java.math.BigDecimal low = getBigDecimal(quoteNode.path("low").get(i));
                java.math.BigDecimal close = getBigDecimal(quoteNode.path("close").get(i));
                java.math.BigDecimal adjClose = adjCloseNode != null && !adjCloseNode.isMissingNode()
                        ? getBigDecimal(adjCloseNode.path("adjclose").get(i))
                        : close;
                long volume = quoteNode.path("volume").get(i) != null ? quoteNode.path("volume").get(i).asLong() : 0L;

                if (close != null) {
                    history.add(new HistoricalQuote(symbol, date, open, low, high, close, adjClose, volume));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to fetch custom Yahoo v8 API for " + symbol, e);
        }
        return history;
    }

    private java.math.BigDecimal getBigDecimal(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode())
            return null;
        return new java.math.BigDecimal(node.asText());
    }

    /**
     * Fetch multiple stocks at once (single batch request)
     */
    public Map<String, Stock> getMultipleStocks(String[] symbols) throws IOException {
        return YahooFinance.get(symbols);
    }

    /**
     * Validate if a symbol exists on Yahoo Finance
     */
    public boolean isValidSymbol(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol);
            return stock != null && stock.getQuote() != null && stock.getQuote().getPrice() != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get real-time quote data
     */
    public StockQuote getQuote(String symbol) throws IOException {
        Stock stock = YahooFinance.get(symbol);
        return stock.getQuote();
    }

    /**
     * Get stock statistics (PE, EPS, market cap, etc.)
     */
    public StockStats getStats(String symbol) throws IOException {
        Stock stock = YahooFinance.get(symbol);
        return stock.getStats();
    }
}
