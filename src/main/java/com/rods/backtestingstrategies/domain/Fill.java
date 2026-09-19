package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record Fill(
        SignalAction action,
        LocalDate signalDate,
        LocalDate executionDate,
        BigDecimal requestedPrice,
        BigDecimal fillPrice,
        BigDecimal quantity,
        BigDecimal fees,
        BigDecimal cashAfter,
        BigDecimal positionAfter,
        BigDecimal equityAfter,
        boolean forcedLiquidation) {

    public Fill {
        action = Objects.requireNonNull(action, "action is required");
        if (action == SignalAction.HOLD) {
            throw new DomainValidationException("HOLD cannot create a fill");
        }
        signalDate = Objects.requireNonNull(signalDate, "signalDate is required");
        executionDate = Objects.requireNonNull(executionDate, "executionDate is required");
        if (executionDate.isBefore(signalDate)) {
            throw new DomainValidationException("fill cannot predate its signal");
        }
        requestedPrice = positive(requestedPrice, "requestedPrice");
        fillPrice = positive(fillPrice, "fillPrice");
        quantity = positive(quantity, "quantity");
        fees = nonNegative(fees, "fees");
        cashAfter = Decimal.value(cashAfter, "cashAfter");
        positionAfter = nonNegative(positionAfter, "positionAfter");
        equityAfter = Decimal.value(equityAfter, "equityAfter");
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        BigDecimal normalized = Decimal.value(value, field);
        if (normalized.signum() <= 0) {
            throw new DomainValidationException(field + " must be positive");
        }
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal normalized = Decimal.value(value, field);
        if (normalized.signum() < 0) {
            throw new DomainValidationException(field + " must be non-negative");
        }
        return normalized;
    }
}
