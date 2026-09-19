package com.rods.backtestingstrategies.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Provider-normalized values before an adjustment policy maps them into the domain candle. */
public record ProviderDailyCandle(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        long volume) {}
