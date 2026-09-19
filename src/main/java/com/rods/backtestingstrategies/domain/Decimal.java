package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Shared precision policy for all backtest calculations. */
public final class Decimal {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE, ROUNDING);
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Decimal() {}

    public static BigDecimal value(BigDecimal value, String field) {
        return Objects.requireNonNull(value, field + " is required").setScale(SCALE, ROUNDING);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, SCALE, ROUNDING);
    }

    public static BigDecimal percentage(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(SCALE, ROUNDING);
    }
}
