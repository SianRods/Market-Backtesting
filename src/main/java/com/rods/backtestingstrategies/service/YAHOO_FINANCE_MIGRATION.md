# Yahoo Finance Implementation & Migration Details

**Date:** 2026-02-06
**Module:** Service Layer

## Overview
This document details the changes made to the backend to integrate **Yahoo Finance** as the primary provider for historical market data (Candles), replacing the Alpha Vantage implementation for that specific purpose. The system now operates in a **Hybrid Mode**, using Yahoo Finance for market data and keeping Alpha Vantage for symbol search.

---

## 1. Dependencies (`pom.xml`)

We added the `YahooFinanceAPI` library to easier interact with Yahoo's public endpoints.

```xml
<!-- Yahoo Finance API -->
<dependency>
    <groupId>com.yahoofinance-api</groupId>
    <artifactId>YahooFinanceAPI</artifactId>
    <version>3.17.0</version>
</dependency>
```

**Note:** No API key configuration is required for this library (`application.yml` remains unchanged regarding Yahoo).

---

## 2. New Class: `YahooFinanceService.java`

**Location:** `com.rods.backtestingstrategies.service.YahooFinanceService`

A new service class was created to encapsulate interactions with the Yahoo Finance library.

-   **Responsibility:** Fetching stock objects containing historical data.
-   **Key Method:** `getDailyStockData(String symbol)`
    -   Uses `YahooFinance.get(symbol)` to retrieve data.
    -   Fetches default history (typically 1 year) or can be configured for more.
    -   Handles `IOException`.

---

## 3. Modified Class: `MarketDataService.java`

**Location:** `com.rods.backtestingstrategies.service.MarketDataService`

This service organizes data flow between the provider and the database.

### Changes:
1.  **Dependency Injection:**
    -   **Added:** `YahooFinanceService`.
    -   **Kept:** `AlphaVantageService` (for search functionality).

2.  **Method: `syncDailyCandles(String symbol)`**
    -   **Old Logic:** Called `alphaVantageService.getDailySeries(symbol)` and mapped `TimeSeriesResponse`.
    -   **New Logic:** Calls `yahooFinanceService.getDailyStockData(symbol)`.
    -   **Data Mapping:**
        -   Iterates over `List<HistoricalQuote>` from Yahoo.
        -   Maps `yahoofinance.histquotes.HistoricalQuote` fields to `Candle` entity.
        -   Converts `Calendar` date to `LocalDate`.
        -   Handles `BigDecimals` by converting to `double`.

3.  **Method: `searchSymbols(String query)`**
    -   **Status:** UNCHANGED.
    -   Continues to use `AlphaVantageService` to find and cache new stock symbols, as Yahoo Finance's public API does not provide an equivalent robust search helper in this library.

---

## 4. Modified Class: `MarketController.java`

**Location:** `com.rods.backtestingstrategies.controller.MarketController`

### Changes:
1.  **Dependency Injection:**
    -   injected `YahooFinanceService`.

2.  **Endpoint: `GET /api/market/daily/{symbol}`**
    -   **Old Logic:** Returned raw Alpha Vantage `TimeSeriesResponse`.
    -   **New Logic:** Returns Yahoo Finance `Stock` object (JSON serialized).
    -   **Impact:** Any frontend component directly consuming this specific endpoint needs to expect the Yahoo Finance JSON structure.

---

## Summary of Migration Status

| feature | Provider | Status |
| :--- | :--- | :--- |
| **Daily Candles** | **Yahoo Finance** | ✅ Migrated |
| **Symbol Search** | **Alpha Vantage** | ⏹️ Kept as is |
| **Database Sync** | **Hybrid** | ✅ Updated to map YF data |
