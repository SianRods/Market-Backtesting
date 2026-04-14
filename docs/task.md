# Implementation Tasks

## Phase 1: Core Migration (AlphaVantage → Yahoo Finance)
- [x] Update `pom.xml` — remove AlphaVantage dep + JitPack, add Yahoo Finance
- [x] Update `application.yml` — remove AlphaVantage API key
- [x] Create `YahooFinanceService.java`
- [x] Update `MarketDataService.java` — use Yahoo Finance
- [x] Delete `AlphaVantageService.java`
- [x] Update `MarketController.java` — remove AlphaVantage refs
- [x] Update `StockSymbol.java` — change source default

## Phase 2: Ticker Database Seeding
- [x] Create `ticker_data.json` resource file (NASDAQ, NYSE, BSE, NSE, LSE, TSE)
- [x] Create `TickerSeederService.java` — populates DB on startup
- [x] Update `StockSymbolRepository.java` — exchange/sector queries
- [x] Update `SymbolController.java` — enhanced search with filtering

## Phase 3: MACD Strategy
- [x] Create `MacdStrategy.java`

## Phase 4: Advanced Performance Metrics
- [x] Create `PerformanceMetrics.java` DTO
- [x] Update `BacktestResult.java` — include metrics + strategyName
- [x] Update `BacktestService.java` — calculate metrics

## Phase 5: Strategy Comparison
- [x] Create `StrategyComparisonResult.java` DTO
- [x] Add comparison endpoint to `BacktestController.java`

## Phase 6: Configurable Strategy Parameters
- [x] Update `BacktestController.java` — accept params
- [x] Update `BacktestService.java` — parameterized strategies

## Phase 7: Real-Time Quote Endpoint
- [x] Create `QuoteSummary.java` DTO
- [x] Add `/api/market/quote/{symbol}` endpoint

## Phase 8: Portfolio Backtesting
- [x] Create `PortfolioRequest.java` DTO
- [x] Create `PortfolioResult.java` DTO
- [x] Add `/api/backtest/portfolio` endpoint

## Phase 9: Cleanup
- [x] Delete `AlphaVantageService.java`
- [x] Delete `StockSymbolRepo.java`
- [x] Update `CorsConfig.java` — added localhost origins
- [x] Verify compilation ✅ BUILD SUCCESS
