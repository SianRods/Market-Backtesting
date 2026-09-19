package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record EquityPoint(LocalDate date, BigDecimal markPrice, BigDecimal equity, BigDecimal quantity, BigDecimal cash) {

    public EquityPoint {
        date = Objects.requireNonNull(date, "date is required");
        markPrice = Decimal.value(markPrice, "markPrice");
        equity = Decimal.value(equity, "equity");
        quantity = Decimal.value(quantity, "quantity");
        cash = Decimal.value(cash, "cash");
        if (quantity.signum() < 0 || cash.signum() < 0) {
            throw new DomainValidationException("long-only state cannot have negative cash or quantity");
        }
    }
}
