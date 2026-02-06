package com.rods.backtestingstrategies.service;

import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.util.Calendar;

@Service
public class YahooFinanceService {

    public Stock getDailyStockData(String symbol) {
        try {
            // Fetch stock with history for the last 5 years (default) or specific range if
            // needed
            // Here we just fetch default which usually gives 1 year or we can specify
            // Ideally we want enough history for backtesting strategies
            // Using default fetch which provides quote and some history
            // To get specific history we can use:
            Calendar from = Calendar.getInstance();
            from.add(Calendar.YEAR, -5); // from 5 years ago
            return YahooFinance.get(symbol, from, Interval.DAILY);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
