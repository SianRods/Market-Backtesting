package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompletedTrade(
        LocalDate entryDate,
        LocalDate exitDate,
        BigDecimal quantity,
        BigDecimal realizedPnl,
        long holdingDays) {}
