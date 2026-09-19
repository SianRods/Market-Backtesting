package com.rods.backtestingstrategies.marketdata;

import java.time.Duration;

public final class MarketDataProviderException extends RuntimeException {

    private final ProviderFailureType type;
    private final String providerId;
    private final Integer httpStatus;
    private final Duration retryAfter;

    public MarketDataProviderException(ProviderFailureType type, String providerId, String message) {
        this(type, providerId, message, null, null, null);
    }

    public MarketDataProviderException(
            ProviderFailureType type,
            String providerId,
            String message,
            Integer httpStatus,
            Duration retryAfter,
            Throwable cause) {
        super(message, cause);
        this.type = type;
        this.providerId = providerId;
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    public ProviderFailureType type() {
        return type;
    }

    public String providerId() {
        return providerId;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
