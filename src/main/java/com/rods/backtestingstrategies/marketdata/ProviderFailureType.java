package com.rods.backtestingstrategies.marketdata;

public enum ProviderFailureType {
    INVALID_SYMBOL,
    UNAVAILABLE_DATA,
    THROTTLED,
    TIMEOUT,
    MALFORMED_RESPONSE,
    PROVIDER_UNAVAILABLE,
    UNSUPPORTED_CAPABILITY
}
