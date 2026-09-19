package com.rods.backtestingstrategies.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleNormalizerTest {

    private final CandleNormalizer normalizer = new CandleNormalizer();

    @Test
    void derivesBackAdjustedOhlcOnlyWhenAdjustedCloseExists() {
        DailyCandleResponse response = response(new ProviderDailyCandle(Instant.parse("2024-01-02T14:30:00Z"),
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"), new BigDecimal("10"), new BigDecimal("5"), 100));

        NormalizedCandle candle = normalizer.normalize(response, AdjustmentPolicy.BACK_ADJUSTED_OHLC, null).getFirst();

        assertEquals(new BigDecimal("0.50000000"), candle.adjustmentFactor());
        assertEquals(new BigDecimal("5.00000000"), candle.domainCandle().open());
    }

    @Test
    void rejectsBackAdjustmentWhenYahooDoesNotSupplyAdjustedClose() {
        DailyCandleResponse response = response(new ProviderDailyCandle(Instant.parse("2024-01-02T14:30:00Z"),
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"), new BigDecimal("10"), null, 100));

        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> normalizer.normalize(response, AdjustmentPolicy.BACK_ADJUSTED_OHLC, null));

        assertEquals(ProviderFailureType.UNSUPPORTED_CAPABILITY, exception.type());
    }

    @Test
    void rejectsNonMonotonicProviderTimestamps() {
        ProviderDailyCandle later = new ProviderDailyCandle(Instant.parse("2024-01-03T14:30:00Z"),
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"), new BigDecimal("10"), null, 100);
        ProviderDailyCandle earlier = new ProviderDailyCandle(Instant.parse("2024-01-02T14:30:00Z"),
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"), new BigDecimal("10"), null, 100);
        DailyCandleResponse response = new DailyCandleResponse("AAPL", List.of(later, earlier), ZoneId.of("America/New_York"), null,
                "USD", "EQUITY", false, Instant.parse("2024-01-04T00:00:00Z"),
                new ProviderMetadata("YAHOO", "test", java.util.Set.of(ProviderCapability.DAILY_OHLCV)));

        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> normalizer.normalize(response, AdjustmentPolicy.RAW_OHLC, null));

        assertEquals(ProviderFailureType.MALFORMED_RESPONSE, exception.type());
    }

    private DailyCandleResponse response(ProviderDailyCandle candle) {
        return new DailyCandleResponse("AAPL", List.of(candle), ZoneId.of("America/New_York"), null, "USD", "EQUITY",
                candle.adjustedClose() != null, Instant.parse("2024-01-03T00:00:00Z"),
                new ProviderMetadata("YAHOO", "test", java.util.Set.of(ProviderCapability.DAILY_OHLCV)));
    }
}
