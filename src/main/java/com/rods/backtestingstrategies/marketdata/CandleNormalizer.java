package com.rods.backtestingstrategies.marketdata;

import com.rods.backtestingstrategies.domain.Candle;
import com.rods.backtestingstrategies.domain.Decimal;
import com.rods.backtestingstrategies.domain.DomainValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CandleNormalizer {

    public List<NormalizedCandle> normalize(DailyCandleResponse response, AdjustmentPolicy policy, ZoneId fallbackTimezone) {
        ZoneId timezone = response.exchangeTimezone() == null ? fallbackTimezone : response.exchangeTimezone();
        if (timezone == null) {
            throw new MarketDataProviderException(ProviderFailureType.UNSUPPORTED_CAPABILITY, response.providerMetadata().providerId(),
                    "Yahoo did not provide a usable exchange timezone and no verified stored timezone exists");
        }
        List<NormalizedCandle> normalized = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        LocalDate previousDate = null;
        for (ProviderDailyCandle source : response.candles()) {
            LocalDate date = source.timestamp().atZone(timezone).toLocalDate();
            if (!dates.add(date)) {
                throw new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, response.providerMetadata().providerId(),
                        "Yahoo returned duplicate session date " + date);
            }
            if (previousDate != null && !date.isAfter(previousDate)) {
                throw new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, response.providerMetadata().providerId(),
                        "Yahoo session dates are not strictly increasing");
            }
            BigDecimal factor = source.adjustedClose() == null ? null
                    : Decimal.divide(source.adjustedClose(), source.close());
            if (policy == AdjustmentPolicy.BACK_ADJUSTED_OHLC && factor == null) {
                throw new MarketDataProviderException(ProviderFailureType.UNSUPPORTED_CAPABILITY, response.providerMetadata().providerId(),
                        "Back-adjusted OHLC requires an adjusted close for " + date);
            }
            BigDecimal open = adjust(source.open(), factor, policy);
            BigDecimal high = adjust(source.high(), factor, policy);
            BigDecimal low = adjust(source.low(), factor, policy);
            BigDecimal close = adjust(source.close(), factor, policy);
            try {
                Candle domain = new Candle(response.symbol(), date, open, high, low, close, source.volume());
                normalized.add(new NormalizedCandle(domain, date, source.open(), source.high(), source.low(), source.close(),
                        source.adjustedClose(), factor, source.volume()));
            } catch (DomainValidationException exception) {
                throw new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, response.providerMetadata().providerId(),
                        "Yahoo candle failed validation on " + date, null, null, exception);
            }
            previousDate = date;
        }
        return List.copyOf(normalized);
    }

    private BigDecimal adjust(BigDecimal raw, BigDecimal factor, AdjustmentPolicy policy) {
        return policy == AdjustmentPolicy.BACK_ADJUSTED_OHLC ? raw.multiply(factor).setScale(Decimal.SCALE, Decimal.ROUNDING) : raw;
    }
}
