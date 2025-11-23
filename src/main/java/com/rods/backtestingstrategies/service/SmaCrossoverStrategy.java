package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.entity.Candle;
import com.rods.backtestingstrategies.entity.SignalType;
import com.rods.backtestingstrategies.entity.Strategy;
import com.rods.backtestingstrategies.entity.TradeSignal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SmaCrossoverStrategy implements Strategy {

    @Override
    public List<TradeSignal> generateSignals(List<Candle> candles) {
        // The length of the windows is entirely dependend on the type of the
        // commodities being traded

        // if we don't have the latest data we then have to keep the window short
        int shortWindow = 10;
        int longWindow = 20;

        List<Double> shortSma = sma(candles, shortWindow);
        List<Double> longSma = sma(candles, longWindow);

        List<TradeSignal> signals = new ArrayList<>();

        for (int i = longWindow; i < candles.size(); i++) {
            double prevShort = shortSma.get(i - 1);
            double prevLong = longSma.get(i - 1);
            double currShort = shortSma.get(i);
            double currLong = longSma.get(i);

            Candle candle = candles.get(i);

            // Cross up: BUY
            if (prevShort <= prevLong && currShort > currLong) {
                signals.add(new TradeSignal(candle.getDate(), SignalType.BUY, candle.getClosePrice()));
            }
            // Cross down: SELL
            if (prevShort >= prevLong && currShort < currLong) {
                signals.add(new TradeSignal(candle.getDate(), SignalType.SELL, candle.getClosePrice()));
            }
        }
        return signals;
    }

    private List<Double> sma(List<Candle> candles, int window) {
        List<Double> result = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < candles.size(); i++) {
            sum += candles.get(i).getClosePrice();
            if (i >= window) {
                sum -= candles.get(i - window).getClosePrice();
            }
            if (i >= window - 1) {
                result.add(sum / window);
            } else {
                result.add(Double.NaN); // or 0
            }
        }
        return result;
    }

    @Override
    public String getName() {
        return "SMA Crossover";
    }
}
