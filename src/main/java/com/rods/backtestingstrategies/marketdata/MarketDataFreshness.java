package com.rods.backtestingstrategies.marketdata;

import java.time.Instant;
import java.time.LocalDate;

public record MarketDataFreshness(
        FreshnessStatus status,
        LocalDate latestSessionDate,
        Instant checkedAt,
        String calendarConfidence,
        ProviderFailureType refreshFailure) {}
