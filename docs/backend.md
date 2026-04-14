# 📡 Backtesting Platform — Backend API Documentation

> **Base URL**: `http://localhost:8080` (local) or your deployed Render URL  
> **Content-Type**: `application/json`  
> **CORS**: Enabled for `http://localhost:3000`, `http://localhost:5173`, `https://backtest-livid.vercel.app`

---

## Table of Contents

1. [Quick Start — Suggested Frontend Flow](#1-quick-start--suggested-frontend-flow)
2. [Symbol Search & Discovery](#2-symbol-search--discovery)
3. [Market Data (Candles & Charts)](#3-market-data-candles--charts)
4. [Real-Time Stock Quotes](#4-real-time-stock-quotes)
5. [Backtesting — Single Strategy](#5-backtesting--single-strategy)
6. [Backtesting — Strategy Comparison](#6-backtesting--strategy-comparison)
7. [Backtesting — Portfolio Level](#7-backtesting--portfolio-level)
8. [Admin / Utility Endpoints](#8-admin--utility-endpoints)
9. [Enums & Constants Reference](#9-enums--constants-reference)
10. [Complete JSON Response Shapes](#10-complete-json-response-shapes)
11. [Error Handling](#11-error-handling)
12. [Frontend Integration Tips](#12-frontend-integration-tips)

---

## 1. Quick Start — Suggested Frontend Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│  STEP 1: User lands on app                                            │
│  → Fetch exchange list:  GET /api/symbols/exchanges                   │
│  → Fetch sector list:    GET /api/symbols/sectors                     │
│  → Show search bar with exchange/sector dropdowns                     │
├─────────────────────────────────────────────────────────────────────────┤
│  STEP 2: User searches for a stock                                    │
│  → As user types: GET /api/symbols/search?query=APP&exchange=NASDAQ   │
│  → Render autocomplete dropdown with matching tickers                 │
├─────────────────────────────────────────────────────────────────────────┤
│  STEP 3: User selects a stock (e.g., AAPL)                           │
│  → Fetch candle data:   GET /api/market/stock/AAPL                    │
│  → Fetch live quote:    GET /api/market/quote/AAPL                    │
│  → Render candlestick/line chart + quote card                         │
├─────────────────────────────────────────────────────────────────────────┤
│  STEP 4: User runs a backtest                                         │
│  → Single: POST /api/backtest/AAPL?strategy=SMA&capital=100000        │
│  → Render equity curve, trade markers, and performance metrics        │
├─────────────────────────────────────────────────────────────────────────┤
│  STEP 5 (Advanced): Strategy comparison                               │
│  → POST /api/backtest/compare/AAPL?capital=100000                     │
│  → Show side-by-side table of all strategies with rankings            │
├─────────────────────────────────────────────────────────────────────────┤
│  STEP 6 (Advanced): Portfolio backtest                                │
│  → POST /api/backtest/portfolio (with JSON body)                      │
│  → Show portfolio-level aggregate metrics + per-stock breakdown       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Symbol Search & Discovery

The backend auto-seeds **180+ tickers** from **6 global exchanges** (NASDAQ, NYSE, NSE, BSE, LSE, TSE) on first startup. All search is performed against this local database — **instant, no external API calls**.

---

### 2.1 `GET /api/symbols/search`

**Search symbols by ticker or company name.** Ideal for autocomplete/typeahead.

| Param      | Type   | Required | Description |
|------------|--------|----------|-------------|
| `query`    | string | ✅       | Search keyword (min 1 char). Matches symbol prefix OR company name substring |
| `exchange` | string | ❌       | Filter by exchange: `NASDAQ`, `NYSE`, `NSE`, `BSE`, `LSE`, `TSE` |

**Example requests:**
```
GET /api/symbols/search?query=APP
GET /api/symbols/search?query=Reliance&exchange=NSE
GET /api/symbols/search?query=T&exchange=NASDAQ
```

**Response:** `200 OK` — Array of `StockSymbol`

```json
[
  {
    "id": 1,
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "type": "Equity",
    "exchange": "NASDAQ",
    "region": "United States",
    "marketOpen": "09:30",
    "marketClose": "16:00",
    "timezone": "US/Eastern",
    "currency": "USD",
    "sector": "Technology",
    "industry": "Consumer Electronics",
    "matchScore": 1.0,
    "lastFetched": "2026-03-28T09:30:00",
    "source": "TICKER_SEED"
  }
]
```

> **Frontend Tip:** Debounce the search input by 300ms and call this endpoint as the user types. Show results in a dropdown. Display `symbol`, `name`, `exchange`, and `sector` in each row.

---

### 2.2 `GET /api/symbols/exchange/{exchange}`

**List ALL symbols for a given exchange.**

| Param      | Type   | Location | Description |
|------------|--------|----------|-------------|
| `exchange` | string | path     | Exchange name: `NASDAQ`, `NYSE`, `NSE`, `BSE`, `LSE`, `TSE` |

```
GET /api/symbols/exchange/NSE
```

**Response:** `200 OK` — Array of `StockSymbol` (same shape as above), sorted alphabetically by symbol.

---

### 2.3 `GET /api/symbols/sector/{sector}`

**List all symbols in a given sector.** Use the exact sector name from the sectors endpoint.

```
GET /api/symbols/sector/Technology
```

**Response:** `200 OK` — Array of `StockSymbol`

---

### 2.4 `GET /api/symbols/exchanges`

**Get all available exchange names.**

```
GET /api/symbols/exchanges
```

**Response:** `200 OK`

```json
["BSE", "LSE", "NASDAQ", "NSE", "NYSE", "TSE"]
```

---

### 2.5 `GET /api/symbols/sectors`

**Get all available sector names.**

```
GET /api/symbols/sectors
```

**Response:** `200 OK`

```json
[
  "Communication Services",
  "Consumer Cyclical",
  "Consumer Defensive",
  "Energy",
  "Financial Services",
  "Healthcare",
  "Industrials",
  "Materials",
  "Technology",
  "Utilities"
]
```

---

### 2.6 `GET /api/symbols/stats`

**Ticker database summary statistics.**

```
GET /api/symbols/stats
```

**Response:** `200 OK`

```json
{
  "totalSymbols": 180,
  "exchanges": ["BSE", "LSE", "NASDAQ", "NSE", "NYSE", "TSE"],
  "sectors": ["Communication Services", "Consumer Cyclical", "..."]
}
```

---

## 3. Market Data (Candles & Charts)

### 3.1 `GET /api/market/stock/{symbol}`

**Fetch historical OHLCV (candle) data for charting.** Data is cached in PostgreSQL. On first request for a symbol, the backend fetches **5 years of daily data** from Yahoo Finance and stores it locally. Subsequent calls return from DB.

| Param    | Type   | Location | Description |
|----------|--------|----------|-------------|
| `symbol` | string | path     | Ticker symbol exactly as listed (e.g., `AAPL`, `RELIANCE.NS`, `7203.T`) |

```
GET /api/market/stock/AAPL
```

> ⚠️ **First-time fetch for a new symbol may take 2-5 seconds** as it hits Yahoo Finance. Subsequent calls are instant from the database.

**Response:** `200 OK` — Array of `Candle`, ordered by date ascending (oldest first)

```json
[
  {
    "id": 1,
    "symbol": "AAPL",
    "date": "2021-03-29",
    "openPrice": 121.65,
    "highPrice": 122.58,
    "lowPrice": 120.73,
    "closePrice": 121.21,
    "volume": 80819200
  },
  {
    "id": 2,
    "symbol": "AAPL",
    "date": "2021-03-30",
    "openPrice": 120.11,
    "highPrice": 120.40,
    "lowPrice": 118.86,
    "closePrice": 119.90,
    "volume": 85338900
  }
]
```

**`Candle` TypeScript interface:**
```typescript
interface Candle {
  id: number;
  symbol: string;
  date: string;          // "YYYY-MM-DD"
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  volume: number;        // integer
}
```

> **Frontend Tip:** Use this data to render a candlestick chart (with libraries like `lightweight-charts`, `recharts`, or `chart.js`). The `date` field is `YYYY-MM-DD` format.

---

## 4. Real-Time Stock Quotes

### 4.1 `GET /api/market/quote/{symbol}`

**Get a real-time quote snapshot from Yahoo Finance.** Returns live price, change, volume, 52-week range, and key fundamentals.

| Param    | Type   | Location | Description |
|----------|--------|----------|-------------|
| `symbol` | string | path     | Any Yahoo Finance ticker symbol |

```
GET /api/market/quote/AAPL
```

**Response:** `200 OK`

```json
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "exchange": "NasdaqNM",
  "currency": "USD",
  "price": 178.72,
  "change": 2.35,
  "changePercent": 1.33,
  "previousClose": 176.37,
  "open": 177.05,
  "dayHigh": 179.23,
  "dayLow": 176.80,
  "volume": 52431800,
  "avgVolume": 58230400,
  "yearHigh": 199.62,
  "yearLow": 124.17,
  "marketCap": 2789000000000,
  "pe": 29.41,
  "eps": 6.08,
  "dividendYield": 0.52,
  "bookValue": 4.25,
  "priceToBook": 42.05
}
```

**`QuoteSummary` TypeScript interface:**
```typescript
interface QuoteSummary {
  symbol: string;
  name: string;
  exchange: string;
  currency: string;

  // Price
  price: number | null;
  change: number | null;
  changePercent: number | null;
  previousClose: number | null;
  open: number | null;
  dayHigh: number | null;
  dayLow: number | null;

  // Volume
  volume: number | null;
  avgVolume: number | null;

  // 52-Week Range
  yearHigh: number | null;
  yearLow: number | null;

  // Fundamentals
  marketCap: number | null;
  pe: number | null;
  eps: number | null;
  dividendYield: number | null;
  bookValue: number | null;
  priceToBook: number | null;
}
```

**Error response** (if Yahoo Finance is unreachable):
```json
{ "error": "Failed to fetch quote: <error message>" }
```

> **Frontend Tip:** Show this as a quote card above the chart. Color `change` and `changePercent` green for positive, red for negative. Some fields may be `null` — always null-check before rendering.

---

## 5. Backtesting — Single Strategy

### 5.1 `POST /api/backtest/{symbol}`

**Run a backtest for one stock with one strategy.** This is the core endpoint.

| Param         | Type    | Location | Required | Default   | Description |
|---------------|---------|----------|----------|-----------|-------------|
| `symbol`      | string  | path     | ✅       | —         | Ticker symbol |
| `strategy`    | string  | query    | ❌       | `SMA`     | One of: `SMA`, `RSI`, `MACD`, `BUY_AND_HOLD` |
| `capital`     | number  | query    | ❌       | `100000`  | Starting capital |
| `shortPeriod` | integer | query    | ❌       | `20`      | SMA short window |
| `longPeriod`  | integer | query    | ❌       | `50`      | SMA long window |
| `fastPeriod`  | integer | query    | ❌       | `12`      | MACD fast EMA |
| `slowPeriod`  | integer | query    | ❌       | `26`      | MACD slow EMA |
| `signalPeriod`| integer | query    | ❌       | `9`       | MACD signal line |

**Example requests:**
```
POST /api/backtest/AAPL?strategy=SMA&capital=100000
POST /api/backtest/AAPL?strategy=SMA&capital=50000&shortPeriod=10&longPeriod=30
POST /api/backtest/AAPL?strategy=RSI&capital=100000
POST /api/backtest/AAPL?strategy=MACD&capital=100000
POST /api/backtest/AAPL?strategy=MACD&fastPeriod=8&slowPeriod=21&signalPeriod=5
POST /api/backtest/RELIANCE.NS?strategy=BUY_AND_HOLD&capital=500000
```

**Response:** `200 OK` — `BacktestResult`

```json
{
  "startCapital": 100000.0,
  "finalCapital": 112450.75,
  "profitLoss": 12450.75,
  "returnPct": 12.45,
  "strategyName": "SMA Crossover (20, 50)",
  "metrics": {
    "sharpeRatio": 0.85,
    "maxDrawdown": -15.32,
    "winRate": 45.0,
    "avgWin": 3250.50,
    "avgLoss": -1820.30,
    "winLossRatio": 1.79,
    "totalTrades": 20,
    "winningTrades": 9,
    "losingTrades": 11,
    "annualizedReturn": 8.72,
    "profitFactor": 1.45,
    "avgHoldingPeriodDays": 32.5
  },
  "equityCurve": [
    {
      "id": null,
      "date": "2021-03-29",
      "price": 121.21,
      "equity": 100000.0,
      "shares": 0,
      "cash": 100000.0
    },
    {
      "id": null,
      "date": "2021-03-30",
      "price": 119.90,
      "equity": 100000.0,
      "shares": 0,
      "cash": 100000.0
    }
  ],
  "transactions": [
    {
      "id": null,
      "date": "2021-06-15",
      "type": "BUY",
      "price": 129.64,
      "shares": 771,
      "cashAfter": 48.06,
      "equityAfter": 99902.50
    },
    {
      "id": null,
      "date": "2021-09-22",
      "type": "SELL",
      "price": 141.91,
      "shares": 771,
      "cashAfter": 109460.67,
      "equityAfter": 109460.67
    }
  ],
  "crossovers": [
    {
      "id": null,
      "date": "2021-06-15",
      "type": "BULLISH",
      "price": 129.64
    },
    {
      "id": null,
      "date": "2021-09-22",
      "type": "BEARISH",
      "price": 141.91
    }
  ]
}
```

---

### Full TypeScript Interfaces

```typescript
interface BacktestResult {
  startCapital: number;
  finalCapital: number;
  profitLoss: number;
  returnPct: number;
  strategyName: string;
  metrics: PerformanceMetrics;
  equityCurve: EquityPoint[];
  transactions: Transaction[];
  crossovers: CrossOver[];
}

interface PerformanceMetrics {
  sharpeRatio: number;       // Annualized, higher = better. > 1.0 is good
  maxDrawdown: number;       // Negative percentage (e.g., -15.32 means 15.32% worst decline)
  winRate: number;           // Percentage 0-100
  avgWin: number;            // Average $ profit on winning trades
  avgLoss: number;           // Average $ loss on losing trades (negative number)
  winLossRatio: number;      // |avgWin / avgLoss|, > 1.0 means wins are bigger than losses
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  annualizedReturn: number;  // Percentage
  profitFactor: number;      // gross profit / gross loss, > 1.0 = profitable
  avgHoldingPeriodDays: number;
}

interface EquityPoint {
  id: number | null;
  date: string;       // "YYYY-MM-DD"
  price: number;      // Stock close price that day
  equity: number;     // Total portfolio value (cash + shares * price)
  shares: number;     // Shares held
  cash: number;       // Cash balance
}

interface Transaction {
  id: number | null;
  date: string;       // "YYYY-MM-DD"
  type: "BUY" | "SELL";
  price: number;      // Execution price
  shares: number;     // Number of shares traded
  cashAfter: number;  // Cash balance after this trade
  equityAfter: number;// Total portfolio value after this trade
}

interface CrossOver {
  id: number | null;
  date: string;       // "YYYY-MM-DD"
  type: "BULLISH" | "BEARISH";
  price: number;      // Price at crossover event
}
```

---

### Frontend Rendering Guide for Backtest Results

| Data                | What to render                                                |
|---------------------|---------------------------------------------------------------|
| `startCapital`, `finalCapital`, `profitLoss`, `returnPct` | Summary cards at top |
| `strategyName`      | Badge/label identifying the strategy used                     |
| `metrics.sharpeRatio` | Risk-adjusted performance gauge (green > 1.0)               |
| `metrics.maxDrawdown` | Red bar chart showing worst decline                         |
| `metrics.winRate`   | Donut/pie chart: wins vs losses                               |
| `metrics.profitFactor` | Green if > 1.0, red if < 1.0                              |
| `equityCurve[]`     | **Line chart** (X = date, Y = equity). This is the main chart |
| `equityCurve[].price` | Overlay stock price as secondary Y-axis                    |
| `transactions[]`    | **Table** with date, type, price, shares, cashAfter           |
| `transactions[]`    | **Markers on chart** — green ▲ for BUY, red ▼ for SELL       |
| `crossovers[]`      | **Vertical lines on chart** — green dashed for BULLISH, red for BEARISH |

---

## 6. Backtesting — Strategy Comparison

### 6.1 `POST /api/backtest/compare/{symbol}`

**Compare ALL 4 strategies on the same stock.** Returns individual results + rankings.

| Param    | Type   | Location | Required | Default  | Description |
|----------|--------|----------|----------|----------|-------------|
| `symbol` | string | path     | ✅       | —        | Ticker symbol |
| `capital`| number | query    | ❌       | `100000` | Starting capital |

```
POST /api/backtest/compare/AAPL?capital=100000
```

**Response:** `200 OK` — `StrategyComparisonResult`

```json
{
  "symbol": "AAPL",
  "initialCapital": 100000.0,
  "results": {
    "SMA Crossover (20, 50)": { /* full BacktestResult */ },
    "RSI Mean Reversion (14)": { /* full BacktestResult */ },
    "MACD (12, 26, 9)": { /* full BacktestResult */ },
    "Buy & Hold": { /* full BacktestResult */ }
  },
  "rankByReturn": [
    "Buy & Hold",
    "MACD (12, 26, 9)",
    "SMA Crossover (20, 50)",
    "RSI Mean Reversion (14)"
  ],
  "rankBySharpe": [
    "MACD (12, 26, 9)",
    "Buy & Hold",
    "SMA Crossover (20, 50)",
    "RSI Mean Reversion (14)"
  ],
  "bestStrategy": "Buy & Hold"
}
```

**`StrategyComparisonResult` TypeScript interface:**
```typescript
interface StrategyComparisonResult {
  symbol: string;
  initialCapital: number;
  results: Record<string, BacktestResult>;  // key = strategy name
  rankByReturn: string[];    // Best → worst by return %
  rankBySharpe: string[];    // Best → worst by Sharpe Ratio
  bestStrategy: string;      // Name of the winning strategy
}
```

> **Frontend Tip:** Render as a comparison table:
> 
> | Strategy | Return % | Sharpe | Max Drawdown | Win Rate | Trades |
> |----------|----------|--------|--------------|----------|--------|
> | 🥇 Buy & Hold | 45.2% | 1.12 | -18.5% | — | 1 |
> | 🥈 MACD | 32.1% | 1.35 | -12.3% | 52% | 14 |
> | 🥉 SMA | 12.5% | 0.85 | -15.3% | 45% | 20 |
> | RSI | -3.2% | -0.15 | -22.1% | 38% | 28 |
>
> Also overlay all equity curves on the same chart with different colors.

---

## 7. Backtesting — Portfolio Level

### 7.1 `POST /api/backtest/portfolio`

**Run a backtest across multiple stocks with weighted capital allocation.**

**Request Body** (JSON):

```json
{
  "entries": [
    { "symbol": "AAPL", "weight": 0.40 },
    { "symbol": "MSFT", "weight": 0.30 },
    { "symbol": "GOOGL", "weight": 0.30 }
  ],
  "totalCapital": 100000,
  "strategy": "SMA"
}
```

| Field          | Type    | Required | Description |
|----------------|---------|----------|-------------|
| `entries`      | array   | ✅       | List of stocks with weights |
| `entries[].symbol` | string | ✅   | Ticker symbol |
| `entries[].weight` | number | ✅   | Allocation weight (0.0 to 1.0). All weights should sum to 1.0 |
| `totalCapital` | number  | ✅       | Total portfolio capital |
| `strategy`     | string  | ✅       | One of: `SMA`, `RSI`, `MACD`, `BUY_AND_HOLD` |

**Response:** `200 OK` — `PortfolioResult`

```json
{
  "totalCapital": 100000.0,
  "finalValue": 118250.50,
  "totalPnL": 18250.50,
  "totalReturnPct": 18.25,
  "strategyUsed": "SMA Crossover (20, 50)",
  "aggregateMetrics": {
    "sharpeRatio": 0.92,
    "maxDrawdown": -12.50,
    "winRate": 48.0,
    "totalTrades": 15,
    "winningTrades": 7,
    "losingTrades": 8,
    "annualizedReturn": 10.50,
    "avgWin": 0.0,
    "avgLoss": 0.0,
    "winLossRatio": 0.0,
    "profitFactor": 0.0,
    "avgHoldingPeriodDays": 0.0
  },
  "symbolResults": {
    "AAPL": { /* full BacktestResult */ },
    "MSFT": { /* full BacktestResult */ },
    "GOOGL": { /* full BacktestResult */ }
  },
  "allocations": {
    "AAPL": 40000.0,
    "MSFT": 30000.0,
    "GOOGL": 30000.0
  }
}
```

**`PortfolioResult` TypeScript interface:**
```typescript
interface PortfolioRequest {
  entries: { symbol: string; weight: number }[];
  totalCapital: number;
  strategy: "SMA" | "RSI" | "MACD" | "BUY_AND_HOLD";
}

interface PortfolioResult {
  totalCapital: number;
  finalValue: number;
  totalPnL: number;
  totalReturnPct: number;
  strategyUsed: string;
  aggregateMetrics: PerformanceMetrics;
  symbolResults: Record<string, BacktestResult>;  // per-stock results
  allocations: Record<string, number>;             // symbol → $ allocated
}
```

> **Frontend Tip:** Show a pie chart for allocations, a combined equity curve, and a per-stock breakdown table. Let users drag sliders to adjust weights and re-run.

---

## 8. Admin / Utility Endpoints

### 8.1 `POST /api/market/reseed-tickers`

**Manually re-populate the ticker database.** Clears all existing symbols and re-imports from the bundled `ticker_data.json`.

```
POST /api/market/reseed-tickers
```

**Response:** `200 OK`

```json
{
  "message": "Ticker database re-seeded successfully",
  "totalSymbols": 180
}
```

> **Note:** This is an admin-only action. The seeder runs automatically on first startup when the DB is empty. Only call this if you've updated the ticker_data.json and want to refresh.

---

## 9. Enums & Constants Reference

### Strategy Types

| Value          | Name in Response                | Description | Default Parameters |
|----------------|---------------------------------|-------------|--------------------|
| `SMA`          | `SMA Crossover (20, 50)`       | Buy when SMA(short) crosses above SMA(long), sell on cross below | `shortPeriod=20`, `longPeriod=50` |
| `RSI`          | `RSI Mean Reversion (14)`      | Buy when RSI < 30 (oversold), sell when RSI > 70 (overbought) | `period=14` (not configurable yet) |
| `MACD`         | `MACD (12, 26, 9)`             | Buy when MACD crosses above signal line, sell on cross below | `fastPeriod=12`, `slowPeriod=26`, `signalPeriod=9` |
| `BUY_AND_HOLD` | `Buy & Hold`                   | Buy on first day, sell on last day. Baseline benchmark | None |

### Configurable Parameters by Strategy

| Strategy | Parameter      | Query Param    | Default | Valid Range |
|----------|---------------|----------------|---------|-------------|
| SMA      | Short period  | `shortPeriod`  | 20      | 1 – must be < longPeriod |
| SMA      | Long period   | `longPeriod`   | 50      | Must be > shortPeriod |
| MACD     | Fast EMA      | `fastPeriod`   | 12      | 1+ |
| MACD     | Slow EMA      | `slowPeriod`   | 26      | Must be > fastPeriod |
| MACD     | Signal line   | `signalPeriod` | 9       | 1+ |

### Signal Types (in transactions)

| Value  | Meaning |
|--------|---------|
| `BUY`  | Shares purchased |
| `SELL` | Shares sold |
| `HOLD` | No action (not returned in transactions, only used internally) |

### CrossOver Types

| Value     | Meaning |
|-----------|---------|
| `BULLISH` | Buying signal was generated (upward crossover) |
| `BEARISH` | Selling signal was generated (downward crossover) |

### Available Exchanges

| Code    | Full Name                        | Region         | Currency | Ticker Suffix |
|---------|----------------------------------|----------------|----------|---------------|
| `NASDAQ`| NASDAQ Stock Market              | United States  | USD      | None (e.g., `AAPL`) |
| `NYSE`  | New York Stock Exchange          | United States  | USD      | None (e.g., `JPM`) |
| `NSE`   | National Stock Exchange of India | India          | INR      | `.NS` (e.g., `RELIANCE.NS`) |
| `BSE`   | Bombay Stock Exchange            | India          | INR      | `.BO` (e.g., `RELIANCE.BO`) |
| `LSE`   | London Stock Exchange            | United Kingdom | GBP      | `.L` (e.g., `SHEL.L`) |
| `TSE`   | Tokyo Stock Exchange             | Japan          | JPY      | `.T` (e.g., `7203.T`) |

---

## 10. Complete JSON Response Shapes

### `StockSymbol`
```typescript
interface StockSymbol {
  id: number;
  symbol: string;        // "AAPL", "RELIANCE.NS", "7203.T"
  name: string;          // "Apple Inc."
  type: string;          // "Equity"
  exchange: string;      // "NASDAQ"
  region: string;        // "United States"
  marketOpen: string;    // "09:30"
  marketClose: string;   // "16:00"
  timezone: string;      // "US/Eastern"
  currency: string;      // "USD"
  sector: string;        // "Technology"
  industry: string;      // "Consumer Electronics"
  matchScore: number;    // 1.0
  lastFetched: string;   // ISO datetime
  source: string;        // "TICKER_SEED"
}
```

### `Candle`
```typescript
interface Candle {
  id: number;
  symbol: string;
  date: string;           // "YYYY-MM-DD"
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  volume: number;
}
```

### `QuoteSummary`
```typescript
interface QuoteSummary {
  symbol: string;
  name: string;
  exchange: string;
  currency: string;
  price: number | null;
  change: number | null;
  changePercent: number | null;
  previousClose: number | null;
  open: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  volume: number | null;
  avgVolume: number | null;
  yearHigh: number | null;
  yearLow: number | null;
  marketCap: number | null;
  pe: number | null;
  eps: number | null;
  dividendYield: number | null;
  bookValue: number | null;
  priceToBook: number | null;
}
```

### `BacktestResult`
```typescript
interface BacktestResult {
  startCapital: number;
  finalCapital: number;
  profitLoss: number;
  returnPct: number;
  strategyName: string;
  metrics: PerformanceMetrics;
  equityCurve: EquityPoint[];
  transactions: Transaction[];
  crossovers: CrossOver[];
}
```

### `PerformanceMetrics`
```typescript
interface PerformanceMetrics {
  sharpeRatio: number;
  maxDrawdown: number;          // Negative % (e.g., -15.32)
  winRate: number;              // 0-100
  avgWin: number;
  avgLoss: number;
  winLossRatio: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  annualizedReturn: number;     // Percentage
  profitFactor: number;
  avgHoldingPeriodDays: number;
}
```

### `EquityPoint`
```typescript
interface EquityPoint {
  id: number | null;
  date: string;
  price: number;
  equity: number;
  shares: number;
  cash: number;
}
```

### `Transaction`
```typescript
interface Transaction {
  id: number | null;
  date: string;
  type: "BUY" | "SELL";
  price: number;
  shares: number;
  cashAfter: number;
  equityAfter: number;
}
```

### `CrossOver`
```typescript
interface CrossOver {
  id: number | null;
  date: string;
  type: "BULLISH" | "BEARISH";
  price: number;
}
```

### `StrategyComparisonResult`
```typescript
interface StrategyComparisonResult {
  symbol: string;
  initialCapital: number;
  results: Record<string, BacktestResult>;
  rankByReturn: string[];
  rankBySharpe: string[];
  bestStrategy: string;
}
```

### `PortfolioRequest`
```typescript
interface PortfolioRequest {
  entries: { symbol: string; weight: number }[];
  totalCapital: number;
  strategy: string;
}
```

### `PortfolioResult`
```typescript
interface PortfolioResult {
  totalCapital: number;
  finalValue: number;
  totalPnL: number;
  totalReturnPct: number;
  strategyUsed: string;
  aggregateMetrics: PerformanceMetrics;
  symbolResults: Record<string, BacktestResult>;
  allocations: Record<string, number>;
}
```

---

## 11. Error Handling

| Scenario | HTTP Status | Response |
|----------|-------------|----------|
| Symbol not found on Yahoo Finance | `200 OK` with empty candles array | `[]` |
| Quote fetch failure | `500 Internal Server Error` | `{ "error": "Failed to fetch quote: ..." }` |
| Invalid strategy type | `400 Bad Request` | Spring default error response |
| Symbol not found (quote) | `404 Not Found` | Empty response body |
| Invalid backtest params (shortPeriod >= longPeriod) | `500 Internal Server Error` | Java exception in response |
| No request body for portfolio endpoint | `400 Bad Request` | Spring default error response |

> **Frontend Tip:** Always wrap API calls in try/catch. Show a user-friendly error toast/modal when errors occur. For the candles endpoint, check if the response array is empty and show "No data available for this symbol" message.

---

## 12. Frontend Integration Tips

### Recommended Libraries
- **Charts:** `lightweight-charts` (TradingView-style) or `recharts` for equity curves
- **HTTP:** `axios` or native `fetch`
- **State:** React Query / TanStack Query for caching API responses

### Example React Hook (fetch + debounce search)
```typescript
// useSymbolSearch.ts
import { useState, useEffect } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export function useSymbolSearch(query: string, exchange?: string) {
  const [results, setResults] = useState<StockSymbol[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!query || query.length < 1) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      const params = new URLSearchParams({ query });
      if (exchange) params.append('exchange', exchange);
      
      const res = await fetch(`${API_BASE}/api/symbols/search?${params}`);
      const data = await res.json();
      setResults(data);
      setLoading(false);
    }, 300);

    return () => clearTimeout(timer);
  }, [query, exchange]);

  return { results, loading };
}
```

### Example: Run Backtest
```typescript
async function runBacktest(
  symbol: string,
  strategy: string = 'SMA',
  capital: number = 100000,
  params?: Record<string, number>
): Promise<BacktestResult> {
  const queryParams = new URLSearchParams({
    strategy,
    capital: String(capital),
  });
  
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      queryParams.append(key, String(value));
    });
  }

  const res = await fetch(
    `${API_BASE}/api/backtest/${symbol}?${queryParams}`,
    { method: 'POST' }
  );
  return res.json();
}

// Usage
const result = await runBacktest('AAPL', 'SMA', 100000, { shortPeriod: 10, longPeriod: 30 });
```

### Example: Portfolio Backtest
```typescript
async function runPortfolioBacktest(request: PortfolioRequest): Promise<PortfolioResult> {
  const res = await fetch(`${API_BASE}/api/backtest/portfolio`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  return res.json();
}

// Usage
const result = await runPortfolioBacktest({
  entries: [
    { symbol: 'AAPL', weight: 0.4 },
    { symbol: 'MSFT', weight: 0.3 },
    { symbol: 'GOOGL', weight: 0.3 },
  ],
  totalCapital: 100000,
  strategy: 'SMA',
});
```

### Key UI Components to Build

| Component | Primary API | Data Used |
|-----------|-------------|-----------|
| **Search Bar** (autocomplete) | `GET /api/symbols/search` | `symbol`, `name`, `exchange` |
| **Exchange/Sector Filters** | `GET /api/symbols/exchanges` + `GET /api/symbols/sectors` | Dropdown values |
| **Stock Quote Card** | `GET /api/market/quote/{symbol}` | Price, change, volume, fundamentals |
| **Candlestick / Price Chart** | `GET /api/market/stock/{symbol}` | OHLCV candle data |
| **Strategy Selector** | None (hardcoded) | `SMA`, `RSI`, `MACD`, `BUY_AND_HOLD` |
| **Parameter Controls** | None (UI-only) | Sliders/inputs for periods |
| **Equity Curve Chart** | `POST /api/backtest/{symbol}` | `equityCurve[]` |
| **Trade Table** | `POST /api/backtest/{symbol}` | `transactions[]` |
| **Metrics Dashboard** | `POST /api/backtest/{symbol}` | `metrics.*` |
| **Strategy Comparison Table** | `POST /api/backtest/compare/{symbol}` | `results`, `rankByReturn` |
| **Portfolio Builder** | UI-only → `POST /api/backtest/portfolio` | `entries`, weights, results |
| **Portfolio Pie Chart** | `POST /api/backtest/portfolio` | `allocations` |

---

*Generated for BacktestingStrategies v0.0.1-SNAPSHOT — Last updated: 2026-03-28*
