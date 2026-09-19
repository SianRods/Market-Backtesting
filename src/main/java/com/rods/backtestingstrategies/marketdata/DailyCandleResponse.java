package com.rods.backtestingstrategies.marketdata;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record DailyCandleResponse(
        String symbol,
        List<ProviderDailyCandle> candles,
        ZoneId exchangeTimezone,
        Instant regularSessionEnd,
        String currency,
        String instrumentType,
        boolean adjustedCloseAvailable,
        Instant fetchedAt,
        ProviderMetadata providerMetadata) {

    public DailyCandleResponse {
        candles = List.copyOf(candles);
    }
}
