package com.rods.backtestingstrategies.marketdata;

import java.util.Set;

public record ProviderMetadata(String providerId, String adapterVersion, Set<ProviderCapability> capabilities) {

    public ProviderMetadata {
        capabilities = Set.copyOf(capabilities);
    }
}
