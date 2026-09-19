package com.rods.backtestingstrategies.marketdata;

import java.time.ZoneId;

public record MarketInstrument(
        String symbol,
        String currency,
        String instrumentType,
        ZoneId exchangeTimezone,
        ProviderMetadata providerMetadata) {}
