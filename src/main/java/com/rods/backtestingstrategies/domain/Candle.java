package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Immutable, provider-independent daily candle. */
public record Candle(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {

    public Candle {
        symbol = Objects.requireNonNull(symbol, "symbol is required").trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new DomainValidationException("symbol is required");
        }
        date = Objects.requireNonNull(date, "date is required");
        open = positive(open, "open");
        high = positive(high, "high");
        low = positive(low, "low");
        close = positive(close, "close");
        if (volume < 0) {
            throw new DomainValidationException("volume must be non-negative");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0
                || high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new DomainValidationException("OHLC values are inconsistent for " + symbol + " on " + date);
        }
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        BigDecimal normalized = Decimal.value(value, field);
        if (normalized.signum() <= 0) {
            throw new DomainValidationException(field + " must be positive");
        }
        return normalized;
    }
}
