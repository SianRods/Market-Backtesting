package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Real-time quote summary from Yahoo Finance
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuoteSummary {

    private String symbol;
    private String name;
    private String exchange;
    private String currency;

    // Price info
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal previousClose;
    private BigDecimal open;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;

    // Volume
    private Long volume;
    private Long avgVolume;

    // 52-week range
    private BigDecimal yearHigh;
    private BigDecimal yearLow;

    // Fundamentals
    private BigDecimal marketCap;
    private BigDecimal pe;
    private BigDecimal eps;
    private BigDecimal dividendYield;
    private BigDecimal bookValue;
    private BigDecimal priceToBook;
}
