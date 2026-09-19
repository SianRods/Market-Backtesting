package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;

/** Immutable fill assumptions. Commission rates are decimal ratios (for example 0.001 = 0.1%). */
public record ExecutionModel(
        boolean integerSharesOnly,
        BigDecimal fixedCommission,
        BigDecimal commissionRate,
        BigDecimal slippageBps,
        int minimumLot,
        FinalPositionPolicy finalPositionPolicy) {

    public ExecutionModel {
        fixedCommission = nonNegative(fixedCommission, "fixedCommission");
        commissionRate = nonNegative(commissionRate, "commissionRate");
        slippageBps = nonNegative(slippageBps, "slippageBps");
        if (minimumLot <= 0) {
            throw new DomainValidationException("minimumLot must be positive");
        }
        finalPositionPolicy = java.util.Objects.requireNonNull(finalPositionPolicy, "finalPositionPolicy is required");
    }

    public static ExecutionModel defaults() {
        return new ExecutionModel(true, Decimal.ZERO, Decimal.ZERO, Decimal.ZERO, 1, FinalPositionPolicy.MARK_TO_MARKET);
    }

    public BigDecimal buyFillPrice(BigDecimal requestedPrice) {
        return Decimal.value(requestedPrice, "requestedPrice")
                .multiply(Decimal.ONE.add(Decimal.divide(slippageBps, new BigDecimal("10000"))))
                .setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    public BigDecimal sellFillPrice(BigDecimal requestedPrice) {
        return Decimal.value(requestedPrice, "requestedPrice")
                .multiply(Decimal.ONE.subtract(Decimal.divide(slippageBps, new BigDecimal("10000"))))
                .setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    public BigDecimal fees(BigDecimal notional) {
        return fixedCommission.add(notional.multiply(commissionRate)).setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal normalized = Decimal.value(value, field);
        if (normalized.signum() < 0) {
            throw new DomainValidationException(field + " must be non-negative");
        }
        return normalized;
    }
}
