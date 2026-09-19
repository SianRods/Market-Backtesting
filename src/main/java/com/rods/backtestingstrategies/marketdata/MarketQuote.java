package com.rods.backtestingstrategies.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

public record MarketQuote(
        String symbol,
        BigDecimal price,
        Instant observedAt,
        ZoneId exchangeTimezone,
        String currency,
        ProviderMetadata providerMetadata) {}
