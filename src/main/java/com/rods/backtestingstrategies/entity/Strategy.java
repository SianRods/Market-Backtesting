package com.rods.backtestingstrategies.entity;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface Strategy {

    // each of the strategy will have a function which will ingest all the candles
    // for a given symbol
    // and then depending upon the strategy can decide whether to enter in the
    // market with some position
    // at a given time or not

    List<TradeSignal> generateSignals(List<Candle> candles, Integer shortDuration, Integer LongDuration);

    String getName();
}
