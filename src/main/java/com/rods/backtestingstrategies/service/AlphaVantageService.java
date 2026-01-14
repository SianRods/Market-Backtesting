package com.rods.backtestingstrategies.service;

import com.crazzyghost.alphavantage.AlphaVantage;
import com.crazzyghost.alphavantage.Config;
import com.crazzyghost.alphavantage.exchangerate.ExchangeRateResponse;
import com.crazzyghost.alphavantage.parameters.OutputSize;
//import com.crazzyghost.alphavantage.search.response.SearchResponse;
import com.crazzyghost.alphavantage.search.response.SearchResponse;
import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlphaVantageService {


        @Value("${alphavantage.api.key}")
        private String apiKey;

    public AlphaVantageService() {
    }


    // Important to understand the use of Post Construct
    @PostConstruct
    public void initCredentialsPostConstruct(){

    // Initializing the application service by configuring the api key
    Config cfg = Config.builder()
            .key(apiKey)
            .timeOut(10)
            .build();

        // Configuring the alphavantage API using the builder settings
        AlphaVantage.api().init(cfg);
    }


    public ExchangeRateResponse getExchangeRate(String from, String to) {
        return AlphaVantage.api()
                .exchangeRate()
                .fromCurrency(from)
                .toCurrency(to)
                .fetchSync();
    }

    public TimeSeriesResponse getDailySeries(String symbol) {
        return AlphaVantage.api()
                .timeSeries()
                .daily()
                .forSymbol(symbol)
                .outputSize(OutputSize.COMPACT) // or COMPACT -> is available in the free tier (mostly number of days )
                .fetchSync();
    }


    // All the Symbols of the markets properly
    public SearchResponse getSymbols(String symbol) {
        return AlphaVantage.api().search().keywords(symbol).fetchSync();
    }


    // we can always add more api related functions like searching for the appropriate symbols in order to
    // accurately map the and send the request related to a specific company through stock market

}

