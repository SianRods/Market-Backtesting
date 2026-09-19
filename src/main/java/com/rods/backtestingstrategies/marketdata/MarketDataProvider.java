package com.rods.backtestingstrategies.marketdata;

import java.time.LocalDate;
import java.time.ZoneId;

public interface MarketDataProvider {

    ProviderMetadata metadata();

    MarketInstrument lookupInstrument(String symbol);

    default DailyCandleResponse fetchDailyCandles(String symbol, LocalDate inclusiveStart, LocalDate inclusiveEnd) {
        return fetchDailyCandles(symbol, inclusiveStart, inclusiveEnd, null);
    }

    DailyCandleResponse fetchDailyCandles(String symbol, LocalDate inclusiveStart, LocalDate inclusiveEnd, ZoneId exchangeTimezoneHint);

    MarketQuote fetchQuote(String symbol);
}
