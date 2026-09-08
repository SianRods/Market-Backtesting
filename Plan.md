## Overall assessment

The project demonstrates solid Spring Boot fundamentals: controller/service/repository separation, a strategy factory, DB-first market-data reuse, JWT authentication, PostgreSQL/JPA, and a non-root multi-stage Docker image.

However, it is not yet a trustworthy backtesting engine. The largest gaps are backtest correctness, Yahoo integration resilience, database lifecycle management, validation/error semantics, and automated testing. For a backend/system-design portfolio project, fixing those areas will add substantially more value than adding more indicators.

I reviewed 47 Java source files, the Maven/Docker configuration, database entities and repositories, security flow, ticker seed containing 180 symbols across six exchanges, and the existing test suite. I did not modify source files or disturb the existing uncommitted changes.

## Verification result

- Main source compilation succeeds.
- The only automated test fails: `1 test, 1 error`.
- The test disables JDBC and JPA, which removes repository beans while controllers and services still require them. See [BacktestingStrategiesApplicationTests.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/test/java/com/rods/backtestingstrategies/BacktestingStrategiesApplicationTests.java:6).
- `mvnw.cmd test` cannot start Maven on this machine because of a bug in the generated wrapper script’s Windows link-target handling.
- Maven reports six duplicate dependencies in [pom.xml](D:/Projects/Backtesting_Engine/BacktestingStrategies/pom.xml).
- The Docker build explicitly skips tests, so a broken suite does not prevent deployment: [Dockerfile](D:/Projects/Backtesting_Engine/BacktestingStrategies/Dockerfile:12).

## Highest-priority findings

| Priority | Finding | Consequence |
|---|---|---|
| Critical | A signal calculated using candle close executes at that same close | Look-ahead/execution bias inflates results |
| Critical | Invalid symbol/provider failure becomes an empty zero-return backtest | Users receive a plausible but false “successful” result |
| Critical | Yahoo calls have no timeouts, bounded retries, circuit breaker, or rate limiting | Threads may block and concurrent requests can overload Yahoo |
| Critical | Portfolio metrics are weighted averages, not calculated from a portfolio equity curve | Sharpe ratio and drawdown are mathematically invalid |
| Critical | Cross-currency portfolios simply add USD, INR, GBP, and JPY values | Portfolio totals can be meaningless |
| High | Raw close is stored while adjusted close is discarded | Splits and dividends can create artificial profit/loss |
| High | No validation for capital, dates, weights, prices, or indicator periods | Division by zero, NaN, negative allocation, and indexing failures |
| High | Database is managed through `ddl-auto:update` | Non-repeatable and unsafe production schema evolution |
| High | Test coverage is effectively zero and currently red | Strategy and security regressions are undetectable |
| High | Any authenticated user can delete/reseed all ticker data | Destructive administrative capability is insufficiently protected |

## Backtesting correctness

The main problems are in [BacktestService.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/service/BacktestService.java:133).

1. **Same-bar execution bias**

   The strategy reads the current candle close and the simulator buys or sells at that exact close. Unless the system explicitly models a market-on-close order submitted before the close, that price is not available when the signal is made.

   Recommended model:

   - Indicator observes candle `t`.
   - Order is created after candle `t`.
   - Order executes at candle `t+1` open, plus configured slippage and fees.
   - Record both `signalTime` and `executionTime`.

2. **Corporate actions**

   Yahoo provides adjusted close, but [MarketDataService.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/service/MarketDataService.java:70) stores raw OHLC and ignores adjusted close. A stock split can therefore resemble a large crash.

   Store:

   - raw OHLC;
   - adjusted close;
   - split/dividend events where available;
   - provider and adjustment methodology.

   Use either a fully adjusted series or an explicit corporate-action ledger—never an undocumented mixture.

3. **Execution model is too optimistic**

   The engine assumes:

   - unlimited liquidity;
   - zero commissions, taxes, spread, and slippage;
   - immediate full fills;
   - integer shares only;
   - no rejected orders;
   - no market holidays or suspended candles.

   Introduce an `ExecutionModel` with commission, percentage/fixed slippage, fractional-share policy, minimum lot, and optional participation limit.

4. **Open trades are excluded from trade metrics**

   Final portfolio value includes an open position, but win rate, profit factor, and average holding period only use completed buy/sell pairs. Return and trade statistics can therefore describe different economic outcomes. Report realized and unrealized P&L separately and define whether the final position is liquidated.

5. **Metric edge cases**

   Current calculations can produce incorrect or misleading results:

   - `initialCapital <= 0` can produce NaN or invalid CAGR.
   - Drawdown divides by the peak, which may be zero.
   - Profit factor returns `0` when there are no losses; mathematically it is infinite or undefined.
   - A strategy with an open trade reports zero completed trades.
   - Sharpe uses a zero risk-free rate with no disclosure.
   - Population rather than sample volatility is used.
   - Portfolio Sharpe cannot be obtained by averaging component Sharpes.

   Prefer nullable/explicitly undefined metrics over silently returning zero.

6. **Indicator correctness and complexity**

   - [RsiStrategy.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/strategy/RsiStrategy.java:54) implements a simple rolling gain/loss calculation, not standard Wilder-smoothed RSI. Either implement Wilder RSI or label it clearly as a different variant.
   - [MacdStrategy.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/strategy/MacdStrategy.java:81) repeatedly rebuilds EMAs and resets the signal calculation over a short window. This is both non-standard and roughly quadratic over the series.
   - Precompute indicator arrays in one pass, reducing the engine to approximately `O(n)`.
   - Validate `period > 0`, `fast < slow`, `short < long`, and sufficient history.
   - Compare strategies from a common evaluation start so different warm-up periods do not distort rankings.

7. **Events are misnamed**

   Every successful BUY becomes a bullish `CrossOver` and every SELL a bearish crossover, including RSI and buy-and-hold trades. Use a generic `StrategyEvent` or only emit crossovers from crossover-based strategies.

## Market-data and Yahoo Finance improvements

[YahooFinanceService.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/service/YahooFinanceService.java:47) directly calls an undocumented chart endpoint through `HttpURLConnection`.

Important corrections:

- The comment claiming “No enforced rate limits” is unsafe. Yahoo’s API terms explicitly state that rate limits may be imposed at Yahoo’s discretion. The Java library also states that it is not associated with Yahoo. Treat the integration as an unreliable external adapter, not an SLA-backed feed. [Yahoo API terms](https://legal.yahoo.com/us/en/yahoo/terms/product-atos/apiforydn/index.html), [YahooFinanceAPI project](https://github.com/sstrickx/yahoofinance-api).
- Yahoo also notes that historical-data availability varies by instrument and licensing. Review acceptable use before presenting this as anything beyond a portfolio/demo system. [Yahoo historical-data help](https://help.yahoo.com/kb/sln2311.html).

Implementation changes:

- Create a `MarketDataProvider` interface with a Yahoo adapter. This permits a licensed/fallback provider later without touching the engine.
- Use Spring `RestClient` or Java `HttpClient` as a configured singleton.
- Set connect, response, and total-request deadlines.
- Handle 400, 404, 429, and 5xx separately.
- Retry only transient failures with exponential backoff, jitter, and `Retry-After`.
- Add a circuit breaker and bulkhead using Resilience4j.
- Validate Yahoo’s error object and array lengths before indexing.
- Close streams and connections deterministically.
- URL-encode and strictly validate symbols.
- Record `provider`, `fetchedAt`, `range`, `timezone`, and response/data version.
- Never include the still-forming current daily candle in a backtest.
- Do not use the JVM timezone when converting exchange timestamps. The current conversion in [MarketDataService.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/service/MarketDataService.java:62) can shift dates.
- Validate `low <= open/close <= high`, positive prices, nonnegative volume, sorted timestamps, and duplicate dates.

The current refresh algorithm downloads all five years whenever the latest candle appears more than one calendar day old. It will repeatedly refresh during weekends, holidays, and before a foreign exchange closes. Fetch incrementally from the latest stored date and make freshness exchange-calendar-aware.

## Database refinement

The current schema only genuinely persists users, candles, and symbols. `TradeSignal`, `Transaction`, `CrossOver`, and `EquityPoint` are annotated as entities but have no repositories or relationships and are never saved. Meanwhile, the README says backtest results and execution logs are stored.

Choose one clear model:

- If runs are transient, remove `@Entity` from result/value objects and expose dedicated API DTOs.
- If reproducibility is part of the system-design story, create a `backtest_run` aggregate containing user, symbol/universe, strategy parameters, engine version, data version/hash, dates, execution assumptions, status, timestamps, summary metrics, trades, and optionally a compressed/JSONB equity curve.

Database actions:

- Replace `ddl-auto:update` with Flyway migrations and `ddl-auto:validate`.
- Disable SQL logging by default in production: [application.yml](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/resources/application.yml:10).
- Retain only PostgreSQL unless MySQL is an intentional supported profile.
- Store money/prices using `BigDecimal` and PostgreSQL `NUMERIC`; keep `double` for ratios/statistical calculations where appropriate.
- Add `NOT NULL`, precision/scale, and check constraints.
- Add range queries such as `findBySymbolAndDateBetweenOrderByDateAsc`.
- Avoid loading five years of candles when the requested range is smaller.
- Configure Hibernate JDBC batching; `saveAll` with identity-generated IDs is not necessarily a true bulk insert.
- Use a database-native `ON CONFLICT DO NOTHING/UPDATE` upsert.
- Add transaction boundaries to synchronization and reseeding.
- Replace `LOWER(column) LIKE ...` searches with functional indexes, `citext`, or PostgreSQL trigram/full-text search.
- Paginate symbol searches and result history.

## Concurrency and latency

A cold request currently performs a synchronous five-year Yahoo download, duplicate-date query, inserts, rereads all candles, executes the strategy, and returns the complete daily equity curve. Portfolio requests do that serially per symbol; comparison requests reload the same candles once per strategy.

Recommended flow:

```text
Request
  → validate and normalize
  → resolve dataset/version
  → serve fresh DB data
  → execute all requested strategies against one immutable candle list
  → cache summary by dataset version + strategy configuration
```

For missing/stale data:

- Submit a deduplicated ingestion job.
- Return `202 Accepted` with a job ID for expensive cold loads, or serve stale data with a freshness warning while refreshing asynchronously.
- Ensure only one refresh per symbol/date range runs at once.
- Use a bounded executor for portfolio symbols.
- Add request/user concurrency limits.
- Return summary by default; make equity curve and trades optional.
- Enable response compression.
- Measure before adding Redis. Caffeine is enough for one instance; Redis becomes useful for multiple replicas, distributed refresh locks, and shared job/cache state.

Suggested API fields include `dataAsOf`, `dataStatus`, `provider`, `executionModel`, `warnings`, `durationMs`, and `runId`.

## API and edge-case handling

[BacktestController.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/controller/BacktestController.java:33) accepts primitive query parameters without meaningful validation. Portfolio requests are also unvalidated.

Add typed request records with Bean Validation:

- normalized nonblank symbol with an allowed-character pattern;
- `capital > 0`;
- `startDate < endDate`;
- bounded history range;
- positive strategy periods and correct period ordering;
- nonempty portfolio;
- unique symbols;
- every weight in `(0,1]`;
- weights sum to one within a defined tolerance;
- maximum symbol count;
- explicit base currency.

Add a `@RestControllerAdvice` returning Spring `ProblemDetail` responses:

- 400: malformed request/unsupported parameters;
- 401/403: authentication/authorization;
- 404: confirmed unknown symbol;
- 409: duplicate resource;
- 422: insufficient or invalid market data;
- 429: application/provider throttling;
- 502/503/504: upstream provider failure, unavailable service, or timeout.

Do not translate a provider failure into `BacktestResult.empty()`. Empty data, invalid symbol, and Yahoo being unavailable are three different states.

Also:

- Introduce `/api/v1`.
- Publish OpenAPI documentation and examples.
- Avoid returning JPA entities directly.
- Add pagination and response size limits.
- Add idempotency support for persisted/async backtest submissions.

## Security findings

- [JwtAuthenticationFilter.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/security/JwtAuthenticationFilter.java:41) does not catch expired, malformed, or invalid-signature JWT exceptions. These can become 500 responses.
- User roles are stored but never added to `UserDetails`, so authorization cannot use them: [CustomUserDetailsService.java](D:/Projects/Backtesting_Engine/BacktestingStrategies/src/main/java/com/rods/backtestingstrategies/security/CustomUserDetailsService.java:24).
- The destructive ticker reseed endpoint is available to every authenticated user.
- Registration has a check-then-insert race and may return 500 on concurrent duplicate usernames.
- Six-character passwords are weak, and login/register have no throttling.
- CORS is configured in two global locations plus `@CrossOrigin`, creating unnecessary and potentially conflicting policy.
- JWTs have no issuer, audience, token ID, refresh/revocation mechanism, or key-rotation story.

Minimum refinements:

- Populate authorities and enforce `ADMIN` for maintenance endpoints.
- Prefer removing the public reseed endpoint entirely and expose it as an operational job.
- Add authentication rate limiting and generic failure responses.
- Normalize usernames and handle unique-constraint conflicts.
- Validate JWT configuration at startup.
- Include issuer/audience and use short access-token lifetimes.
- Consolidate CORS into one environment-configured policy.
- Add security headers and structured audit events without logging credentials or tokens.

## Testing plan

Build this test pyramid before adding features:

1. **Pure engine unit tests**

   - Known SMA, Wilder RSI, and MACD vectors.
   - Buy/sell execution on the next candle.
   - Fees, slippage, insufficient capital, zero/invalid prices.
   - Single candle, empty history, missing dates, open final position.
   - Split-adjusted data.
   - Metric golden tests for CAGR, Sharpe, drawdown, and profit factor.

2. **Property/invariant tests**

   - Equity always equals cash plus marked holdings.
   - Shares and cash never become negative in a long-only engine.
   - With zero costs, buy-and-hold return matches the adjusted-price return.
   - No transaction occurs before its signal.

3. **Persistence tests**

   Use Testcontainers PostgreSQL to verify migrations, uniqueness, upserts, concurrent synchronization, indexes, and date-range queries.

4. **Provider contract tests**

   Use WireMock fixtures for success, missing values, malformed JSON, 404, 429 with `Retry-After`, timeout, 5xx, split data, and partial current-day candles.

5. **API/security tests**

   MockMvc tests for validation, problem responses, CORS, invalid JWTs, roles, rate limits, and reseed authorization.

6. **Performance tests**

   Define measurable targets, for example:

   - warm single-symbol summary p95 under 250 ms;
   - cached comparison p95 under 500 ms;
   - bounded first-fetch timeout;
   - no duplicate Yahoo fetch under 20 concurrent requests;
   - explicit maximum response size.

## Recommended implementation roadmap

### Phase 0 — Reproducible baseline

- Clean duplicated Maven dependencies and remove unused DTO/classes/imports.
- Repair/regenerate the Maven wrapper.
- Fix the failing context test.
- Add CI for compile, tests, formatting, dependency scanning, and Docker build.
- Stop skipping tests in release builds.
- Add local and production profiles plus Docker Compose for PostgreSQL.
- Repair README encoding, missing images, and outdated Alpha Vantage claims.

**Exit criterion:** a fresh clone builds and tests successfully with one documented command.

### Phase 1 — Financial correctness

- Introduce immutable `Candle`, `Order`, `Fill`, `Position`, and `PortfolioState` domain objects.
- Implement next-bar execution, adjusted prices, fees, slippage, and final-position policy.
- Rewrite indicators as one-pass series calculations.
- Correct all metric definitions and undefined-value handling.
- Add date ranges, common warm-up rules, and benchmark comparison.
- Establish golden unit tests.

**Exit criterion:** deterministic results match independently calculated fixtures.

### Phase 2 — Reliable data layer

- Introduce the provider abstraction.
- Harden Yahoo HTTP handling and validation.
- Add incremental, exchange-aware synchronization.
- Add Flyway, numeric columns, upserts, metadata, and range indexes.
- Prevent concurrent duplicate ingestion.
- Separate data ingestion from request execution.

**Exit criterion:** malformed or unavailable upstream data cannot silently produce a valid result.

### Phase 3 — Production-quality API and security

- Add request/response DTOs, validation, versioning, Problem Details, pagination, and OpenAPI.
- Implement proper authorities, admin operations, JWT failure handling, throttling, and consolidated CORS.
- Add async job semantics for cold portfolio workloads.

**Exit criterion:** every expected failure has a documented stable HTTP response.

### Phase 4 — Performance and observability

- Reuse one candle dataset across strategy comparison.
- Build a synchronized portfolio equity curve.
- Add bounded parallelism, cache keys tied to data versions, compression, and response projections.
- Add Actuator health/readiness, Micrometer metrics, structured logging, correlation IDs, and OpenTelemetry tracing.
- Track Yahoo latency/error/rate-limit metrics, cache hit rate, DB query time, backtest duration, and queue depth.

**Exit criterion:** load tests satisfy documented latency, concurrency, and response-size targets.

### Phase 5 — Portfolio-level system-design showcase

- Base-currency conversion using historical FX series.
- Rebalancing schedules and cash allocation.
- Benchmark/alpha/beta, Sortino, Calmar, exposure, turnover, and fee attribution.
- Persisted reproducible runs with engine/data versions.
- Walk-forward testing and out-of-sample periods.
- Explicit discussion of survivorship bias, look-ahead bias, selection bias, and Yahoo data limitations.

## Recommended target architecture

Keep this a modular monolith; microservices would add complexity without improving the demonstration.

```text
backtest/
  api/             requests, responses, validation
  application/     use cases and job orchestration
  domain/          pure engine, strategies, orders, metrics
  infrastructure/
    marketdata/    Yahoo and future provider adapters
    persistence/   JPA entities, repositories, migrations
    security/      JWT and authorization
  observability/   metrics, tracing, audit events
```

The strongest portfolio narrative would be: reproducible market datasets, bias-aware execution, mathematically valid metrics, resilient provider integration, concurrency-safe caching, and evidence from integration/load tests. That tells a much stronger backend and system-design story than simply increasing the number of strategies.