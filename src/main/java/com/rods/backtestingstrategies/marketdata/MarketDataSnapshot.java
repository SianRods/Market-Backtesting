package com.rods.backtestingstrategies.marketdata;

import com.rods.backtestingstrategies.domain.Candle;
import java.util.List;

/** Validated domain candles with explicit freshness rather than silent empty/stale data. */
public record MarketDataSnapshot(List<Candle> candles, MarketDataFreshness freshness) {

    public MarketDataSnapshot {
        candles = List.copyOf(candles);
    }

    public boolean isFresh() {
        return freshness.status() == FreshnessStatus.FRESH;
    }
}
