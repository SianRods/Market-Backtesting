package com.rods.backtestingstrategies.service;

import com.crazzyghost.alphavantage.AlphaVantage;
import com.crazzyghost.alphavantage.Config;
import com.crazzyghost.alphavantage.exchangerate.ExchangeRateResponse;
import com.crazzyghost.alphavantage.parameters.OutputSize;
import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import org.springframework.stereotype.Service;

@Service
public class AlphaVantageService {

    public AlphaVantageService() {
        Config cfg = Config.builder()
                .key("XKHV09MRZB6O2W90")
                .timeOut(10)
                .build();
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
                .outputSize(OutputSize.FULL) // or COMPACT
                .fetchSync();
    }

    // we can always add more api related functinos like searching for the appropriate symbols in order to
    // accurately map the and send the request related to a specific company through stock market

}

