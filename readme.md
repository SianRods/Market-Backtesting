# Backtesting Strategies API

A Java 21 and Spring Boot backend for retrieving historical market data and evaluating trading strategies. The project is an educational system-design and backend-engineering exercise; it is not investment advice or a production trading system.

## Current capabilities

- Daily OHLCV history cached in PostgreSQL.
- Yahoo Finance-backed history and quote retrieval.
- SMA crossover, RSI, MACD, and buy-and-hold strategies.
- Single-symbol backtests, strategy comparisons, and weighted portfolio backtests.
- Equity curves, transactions, crossover events, and basic performance metrics.
- Username/password registration and login with stateless JWT authentication.
- Curated symbol search across NASDAQ, NYSE, NSE, BSE, LSE, and TSE.

## Current architecture

```text
REST controllers
    -> application services
        -> strategy implementations
        -> Spring Data JPA repositories -> PostgreSQL
        -> Yahoo Finance integration
```

The application is currently a modular Spring Boot monolith. Keeping the backtesting engine, persistence, and provider integration in one deployable unit is intentional at this project size.

## Requirements

- Java 21
- Docker with Docker Compose for local PostgreSQL and integration tests
- Git

The Maven Wrapper is included, so a separate Maven installation is not required.

## Local setup

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Start the backend with the local profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Alternatively, build and start both the application and database. Compose waits for PostgreSQL to become healthy before starting the application:

```powershell
docker compose --profile app up --build
```

The local profile defaults to:

- JDBC URL: `jdbc:postgresql://localhost:5432/backtesting`
- Database username/password: `backtesting` / `backtesting`
- Server port: `8080`
- Ticker seeding enabled

These defaults are for local development only. Override them with environment variables when necessary. `.env.example` lists the supported local values.

## Verification

Run the complete build and test suite:

```powershell
.\mvnw.cmd clean verify
```

The Spring context integration test uses a real PostgreSQL Testcontainer and never contacts Yahoo. It is skipped when Docker is unavailable; CI runs it with Docker enabled.

Run the dependency vulnerability scan:

```powershell
$env:NVD_API_KEY = "your-nvd-api-key"
.\mvnw.cmd -Psecurity -DskipTests verify
```

The security profile fails for vulnerabilities with CVSS 7 or higher. Suppressions must be narrow, justified, owned, and time-limited in `dependency-check-suppressions.xml`.

## Docker image

Build the production image:

```powershell
docker build -t backtesting-strategies .
```

Run it with production configuration:

```powershell
docker run --rm -p 7860:7860 `
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/backtesting" `
  -e DB_USERNAME="backtesting" `
  -e DB_PASSWORD="replace-me" `
  -e JWT_SECRET="replace-with-a-base64-encoded-secret-of-at-least-32-bytes" `
  backtesting-strategies
```

The image uses the `prod` profile, listens on port `7860` by default, runs as a non-root user, and is built only after Maven verification succeeds.

## Configuration

| Variable | Required in production | Default | Purpose |
|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | None outside the image | Select `local`, `test`, or `prod` |
| `DB_URL` | Yes | Local profile has a development value | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | Local profile: `backtesting` | PostgreSQL username |
| `DB_PASSWORD` | Yes | Local profile: `backtesting` | PostgreSQL password |
| `JWT_SECRET` | Yes | Development-only local/test keys | Base64-encoded HMAC key of at least 32 bytes |
| `JWT_EXPIRATION_MS` | No | `86400000` | Access-token lifetime in milliseconds |
| `TICKER_SEEDER_ENABLED` | No | `false` common/prod, `true` local | Enable curated ticker seeding |
| `SERVER_PORT` | No | `8080`, image sets `7860` | HTTP port |
| `JPA_DDL_AUTO` | No | `validate`, local profile uses `update` | Hibernate schema behavior |
| `JPA_SHOW_SQL` | No | `false` | Local SQL output |
| `JPA_FORMAT_SQL` | No | `false` | Local SQL formatting |
| `APP_LOG_LEVEL` | No | `INFO` | Application log level |
| `ROOT_LOG_LEVEL` | No | `INFO` | Production root log level |
| `NVD_API_KEY` | CI/security scan | None | NVD API key for OWASP Dependency-Check |

Production intentionally has no fallback database credentials or JWT secret. Missing required values cause startup to fail rather than starting insecurely.

## Current REST API

Authentication:

- `POST /api/auth/register`
- `POST /api/auth/login`

Authenticated market and backtest operations:

- `GET /api/market/stock/{symbol}`
- `GET /api/market/quote/{symbol}`
- `POST /api/backtest/{symbol}`
- `POST /api/backtest/compare/{symbol}`
- `POST /api/backtest/portfolio`
- `GET /api/symbols/search`
- `GET /api/symbols/exchange/{exchange}`
- `GET /api/symbols/sector/{sector}`
- `GET /api/symbols/exchanges`
- `GET /api/symbols/sectors`
- `GET /api/symbols/stats`

Public monitoring:

- `GET /server/ping`

The current endpoint contract will be replaced by a versioned API in a later refinement phase.

## Important limitations

- Signals currently execute on the same closing price used to calculate them, which introduces execution bias.
- Prices and capital currently use floating-point values.
- Corporate-action adjustment is not consistently applied.
- Fees, slippage, spread, liquidity, and partial fills are not modeled.
- Portfolio calculations do not yet support currency conversion.
- Yahoo refreshes are synchronous and do not yet have production-grade timeout, retry, or circuit-breaker behavior.
- Backtest results are returned to callers but are not persisted as reproducible runs.
- The application uses Hibernate-managed local schema updates until Flyway is introduced in a later phase.

These limitations are tracked in `Plan.md` and the phase plans under the locally ignored `refinement/` directory.

## Yahoo Finance data notice

The project uses Yahoo Finance endpoints through an unaffiliated Java library and a direct chart request. Data availability, correctness, latency, throttling, and licensing are controlled by the provider and are not guaranteed. The integration is suitable for demonstration and learning, not for executing trades or providing regulated financial services. Review Yahoo's current terms and data-provider notices before deploying beyond personal educational use.

## Delivery and quality gates

GitHub Actions verifies the Maven Wrapper on Windows, runs the full verification suite on Linux with Docker, performs a dependency-security scan, and builds the production container. Application logs use SLF4J and must not contain credentials, tokens, cookies, or provider authentication material.
