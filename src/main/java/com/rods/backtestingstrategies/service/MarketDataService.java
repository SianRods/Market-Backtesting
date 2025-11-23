package com.rods.backtestingstrategies.service;

// the main task of this class is to be able to process the data from the alpha vantage endpoints
// and then use the data for saving the candles in the database

// Also note that we are always using the cross-over moving strategies with respect days

import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.repository.CandleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketDataService {

    @Autowired
    private final AlphaVantageService alphaVantageService;
    private final CandleRepository candleRepository;

    public MarketDataService(AlphaVantageService alphaVantageService,
                             CandleRepository candleRepository) {
        this.alphaVantageService = alphaVantageService;
        this.candleRepository = candleRepository;
    }

    public void syncDailyCandles(String symbol) {
        TimeSeriesResponse response = alphaVantageService.getDailySeries(symbol);
        System.out.println(response);


        response.getStockUnits().forEach(unit -> {
            Candle candle = new Candle();
            candle.setSymbol(symbol);
            // -> using a parser to parse the localdate from the internal string implementation of the API
            candle.setDate(LocalDate.parse(unit.getDate()));
            candle.setOpenPrice(unit.getOpen());
            candle.setHighPrice(unit.getHigh());
            candle.setLowPrice(unit.getLow());
            candle.setClosePrice(unit.getClose());
            candle.setVolume((long) unit.getVolume());
            candleRepository.save(candle);
        });
    }

    public List<Candle> getCandles(String symbol) {
        // returning the candles for given stock in ascending order by the dates
        // using this we will calculate and decide the strategies for BUY/SELL when potential crossover happens
        List<Candle> candles= candleRepository.findBySymbolOrderByDateAsc(symbol);
        if(candles.isEmpty()){
            // if there is no availble data in the database sync with the latest market data
            syncDailyCandles(symbol);
        }
        candles=candleRepository.findBySymbolOrderByDateAsc(symbol);
        return candles;
    }
}


