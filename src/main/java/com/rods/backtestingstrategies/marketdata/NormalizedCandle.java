package com.rods.backtestingstrategies.marketdata;

import com.rods.backtestingstrategies.domain.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedCandle(
        Candle domainCandle,
        LocalDate sessionDate,
        BigDecimal rawOpen,
        BigDecimal rawHigh,
        BigDecimal rawLow,
        BigDecimal rawClose,
        BigDecimal adjustedClose,
        BigDecimal adjustmentFactor,
        long volume) {}
