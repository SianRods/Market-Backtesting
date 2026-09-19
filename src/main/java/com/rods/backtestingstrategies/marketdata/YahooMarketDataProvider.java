package com.rods.backtestingstrategies.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for Yahoo's undocumented chart surface. It deliberately exposes no assumed SLA and does
 * not implement cookie/crumb bypasses or rate-limit probing.
 */
@Component
@Slf4j
public class YahooMarketDataProvider implements MarketDataProvider {

    static final String PROVIDER_ID = "YAHOO";
    private static final String ADAPTER_VERSION = "chart-v8-1";
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.DAILY_OHLCV,
            ProviderCapability.ADJUSTED_CLOSE,
            ProviderCapability.CHART_METADATA,
            ProviderCapability.BEST_EFFORT_QUOTE);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MarketDataProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    public YahooMarketDataProvider(
            HttpClient yahooHttpClient,
            ObjectMapper objectMapper,
            MarketDataProperties properties,
            CircuitBreakerRegistry marketDataCircuitBreakerRegistry,
            BulkheadRegistry marketDataBulkheadRegistry) {
        this.httpClient = yahooHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.circuitBreaker = marketDataCircuitBreakerRegistry.circuitBreaker(PROVIDER_ID.toLowerCase(Locale.ROOT));
        this.bulkhead = marketDataBulkheadRegistry.bulkhead(PROVIDER_ID.toLowerCase(Locale.ROOT));
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Market-data circuit breaker transition provider={} transition={}", PROVIDER_ID, event.getStateTransition()));
    }

    @Override
    public ProviderMetadata metadata() {
        return new ProviderMetadata(PROVIDER_ID, ADAPTER_VERSION, CAPABILITIES);
    }

    @Override
    public MarketInstrument lookupInstrument(String symbol) {
        DailyCandleResponse response = fetchDailyCandles(symbol, LocalDate.now(ZoneOffset.UTC).minusDays(14), LocalDate.now(ZoneOffset.UTC));
        return new MarketInstrument(response.symbol(), response.currency(), response.instrumentType(), response.exchangeTimezone(), metadata());
    }

    @Override
    public MarketQuote fetchQuote(String symbol) {
        DailyCandleResponse response = fetchDailyCandles(symbol, LocalDate.now(ZoneOffset.UTC).minusDays(14), LocalDate.now(ZoneOffset.UTC));
        ProviderDailyCandle last = response.candles().getLast();
        return new MarketQuote(response.symbol(), last.close(), last.timestamp(), response.exchangeTimezone(), response.currency(), metadata());
    }

    @Override
    public DailyCandleResponse fetchDailyCandles(
            String symbol, LocalDate inclusiveStart, LocalDate inclusiveEnd, ZoneId exchangeTimezoneHint) {
        String normalizedSymbol = validateSymbol(symbol);
        if (inclusiveStart == null || inclusiveEnd == null || inclusiveEnd.isBefore(inclusiveStart)) {
            throw new MarketDataProviderException(ProviderFailureType.UNAVAILABLE_DATA, PROVIDER_ID, "A valid inclusive date range is required");
        }
        try {
            return bulkhead.executeCallable(CircuitBreaker.decorateCallable(circuitBreaker,
                    () -> fetchWithRetry(normalizedSymbol, inclusiveStart, inclusiveEnd, exchangeTimezoneHint)));
        } catch (CallNotPermittedException | BulkheadFullException exception) {
            throw new MarketDataProviderException(ProviderFailureType.PROVIDER_UNAVAILABLE, PROVIDER_ID,
                    "Yahoo provider is temporarily unavailable due to resilience controls", null, null, exception);
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MarketDataProviderException(ProviderFailureType.PROVIDER_UNAVAILABLE, PROVIDER_ID,
                    "Yahoo provider request failed", null, null, exception);
        }
    }

    private DailyCandleResponse fetchWithRetry(String symbol, LocalDate start, LocalDate end, ZoneId exchangeTimezoneHint) {
        MarketDataProviderException lastFailure = null;
        for (int attempt = 1; attempt <= properties.getRetry().getMaxAttempts(); attempt++) {
            try {
                return fetchOnce(symbol, start, end, exchangeTimezoneHint);
            } catch (MarketDataProviderException exception) {
                lastFailure = exception;
                if (!retryable(exception) || attempt == properties.getRetry().getMaxAttempts()) {
                    throw exception;
                }
                sleep(backoff(attempt, exception.retryAfter()));
            }
        }
        throw lastFailure;
    }

    private DailyCandleResponse fetchOnce(String symbol, LocalDate start, LocalDate end, ZoneId exchangeTimezoneHint) {
        URI uri = chartUri(symbol, start, end, exchangeTimezoneHint);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .header("User-Agent", properties.getYahoo().getUserAgent())
                .timeout(properties.getYahoo().getResponseTimeout())
                .build();
        try {
            CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = future.get(properties.getYahoo().getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 404 || status == 400 || status == 422) {
                throw new MarketDataProviderException(ProviderFailureType.INVALID_SYMBOL, PROVIDER_ID,
                        "Yahoo rejected symbol " + symbol, status, null, null);
            }
            if (status == 429) {
                throw new MarketDataProviderException(ProviderFailureType.THROTTLED, PROVIDER_ID,
                        "Yahoo throttled the request", status, retryAfter(response), null);
            }
            if (status >= 500) {
                throw new MarketDataProviderException(ProviderFailureType.PROVIDER_UNAVAILABLE, PROVIDER_ID,
                        "Yahoo returned server status " + status, status, null, null);
            }
            if (status < 200 || status >= 300) {
                throw new MarketDataProviderException(ProviderFailureType.PROVIDER_UNAVAILABLE, PROVIDER_ID,
                        "Yahoo returned HTTP status " + status, status, null, null);
            }
            return parseChart(symbol, response.body(), Instant.now());
        } catch (TimeoutException exception) {
            throw new MarketDataProviderException(ProviderFailureType.TIMEOUT, PROVIDER_ID, "Yahoo request timed out", null, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(ProviderFailureType.TIMEOUT, PROVIDER_ID, "Yahoo request interrupted", null, null, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            ProviderFailureType type = cause instanceof HttpTimeoutException ? ProviderFailureType.TIMEOUT : ProviderFailureType.PROVIDER_UNAVAILABLE;
            throw new MarketDataProviderException(type, PROVIDER_ID, "Yahoo request failed", null, null, cause);
        }
    }

    DailyCandleResponse parseChart(String requestedSymbol, String body, Instant fetchedAt) {
        try {
            JsonNode chart = objectMapper.readTree(body).path("chart");
            JsonNode error = chart.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String description = error.path("description").asText("Yahoo chart returned an error");
                String normalizedDescription = description.toLowerCase(Locale.ROOT);
                ProviderFailureType type = (normalizedDescription.contains("not found")
                                || normalizedDescription.contains("no data found")
                                || normalizedDescription.contains("symbol may be delisted"))
                        ? ProviderFailureType.INVALID_SYMBOL : ProviderFailureType.UNAVAILABLE_DATA;
                throw new MarketDataProviderException(type, PROVIDER_ID, description);
            }
            JsonNode resultArray = chart.path("result");
            if (!resultArray.isArray() || resultArray.isEmpty() || resultArray.get(0).isNull()) {
                throw new MarketDataProviderException(ProviderFailureType.UNAVAILABLE_DATA, PROVIDER_ID,
                        "Yahoo chart response contains no usable result");
            }
            JsonNode result = resultArray.get(0);
            JsonNode metadata = result.path("meta");
            JsonNode timestamps = requiredArray(result, "timestamp");
            JsonNode quoteArray = requiredArray(result.path("indicators"), "quote");
            if (quoteArray.isEmpty() || quoteArray.get(0).isNull()) {
                throw malformed("quote array contains no object");
            }
            JsonNode quote = quoteArray.get(0);
            JsonNode opens = requiredArray(quote, "open");
            JsonNode highs = requiredArray(quote, "high");
            JsonNode lows = requiredArray(quote, "low");
            JsonNode closes = requiredArray(quote, "close");
            JsonNode volumes = requiredArray(quote, "volume");
            validateArrayLengths(timestamps, opens, highs, lows, closes, volumes);

            ZoneId timezone = timezone(metadata.path("exchangeTimezoneName").asText(null));
            Instant regularSessionEnd = epoch(metadata.path("currentTradingPeriod").path("regular").path("end"));
            JsonNode adjusted = adjustedCloseArray(result.path("indicators"), timestamps.size());
            boolean adjustedAvailable = adjusted != null;
            List<ProviderDailyCandle> candles = new ArrayList<>();
            for (int index = 0; index < timestamps.size(); index++) {
                Instant timestamp = epoch(timestamps.get(index));
                if (timestamp == null) {
                    throw malformed("timestamp is null at index " + index);
                }
                if (isUncertainCurrentSession(timestamp, timezone, regularSessionEnd, fetchedAt)) {
                    continue;
                }
                BigDecimal open = decimal(opens.get(index), "open", index);
                BigDecimal high = decimal(highs.get(index), "high", index);
                BigDecimal low = decimal(lows.get(index), "low", index);
                BigDecimal close = decimal(closes.get(index), "close", index);
                long volume = volume(volumes.get(index), index);
                BigDecimal adjustedClose = adjusted == null || adjusted.get(index).isNull() ? null
                        : decimal(adjusted.get(index), "adjustedClose", index);
                candles.add(new ProviderDailyCandle(timestamp, open, high, low, close, adjustedClose, volume));
            }
            if (candles.isEmpty()) {
                throw new MarketDataProviderException(ProviderFailureType.UNAVAILABLE_DATA, PROVIDER_ID,
                        "Yahoo returned no completed daily candles");
            }
            return new DailyCandleResponse(
                    result.path("meta").path("symbol").asText(requestedSymbol), candles, timezone, regularSessionEnd,
                    metadata.path("currency").asText(null), metadata.path("instrumentType").asText(null), adjustedAvailable,
                    fetchedAt, metadata());
        } catch (MarketDataProviderException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, PROVIDER_ID,
                    "Yahoo chart response could not be normalized", null, null, exception);
        }
    }

    private URI chartUri(String symbol, LocalDate inclusiveStart, LocalDate inclusiveEnd, ZoneId exchangeTimezoneHint) {
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        ZoneId zone = exchangeTimezoneHint == null ? ZoneOffset.UTC : exchangeTimezoneHint;
        long period1 = inclusiveStart.atStartOfDay(zone).toEpochSecond();
        long period2 = inclusiveEnd.plusDays(1).atStartOfDay(zone).toEpochSecond();
        return URI.create(properties.getYahoo().getBaseUri() + "/v8/finance/chart/" + encodedSymbol
                + "?period1=" + period1 + "&period2=" + period2 + "&interval=1d&events=div%2Csplits");
    }

    private String validateSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[A-Za-z0-9.^=\\-]{1,32}")) {
            throw new MarketDataProviderException(ProviderFailureType.INVALID_SYMBOL, PROVIDER_ID,
                    "Symbol must be 1-32 characters from A-Z, 0-9, '.', '-', '^', or '='");
        }
        return symbol.toUpperCase(Locale.ROOT);
    }

    private JsonNode requiredArray(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isArray()) {
            throw malformed(field + " array is missing");
        }
        return value;
    }

    private JsonNode adjustedCloseArray(JsonNode indicators, int expectedLength) {
        JsonNode adjClose = indicators.path("adjclose");
        if (adjClose.isMissingNode() || adjClose.isNull()) {
            return null;
        }
        if (!adjClose.isArray() || adjClose.isEmpty() || !adjClose.get(0).path("adjclose").isArray()) {
            throw malformed("adjclose array is malformed");
        }
        JsonNode values = adjClose.get(0).path("adjclose");
        if (values.size() != expectedLength) {
            throw malformed("adjclose array length does not match timestamp array");
        }
        return values;
    }

    private void validateArrayLengths(JsonNode... arrays) {
        int expected = arrays[0].size();
        if (expected == 0) {
            throw malformed("Yahoo returned an empty timestamp array");
        }
        for (JsonNode array : arrays) {
            if (array.size() != expected) {
                throw malformed("Yahoo chart arrays have inconsistent lengths");
            }
        }
    }

    private BigDecimal decimal(JsonNode value, String field, int index) {
        if (value == null || value.isNull() || !value.isNumber()) {
            throw malformed(field + " is null or non-numeric at index " + index);
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.signum() <= 0) {
            throw malformed(field + " must be positive at index " + index);
        }
        return decimal;
    }

    private long volume(JsonNode value, int index) {
        if (value == null || value.isNull() || !value.canConvertToLong() || value.longValue() < 0) {
            throw malformed("volume is invalid at index " + index);
        }
        return value.longValue();
    }

    private ZoneId timezone(String timezoneName) {
        if (timezoneName == null || timezoneName.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezoneName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant epoch(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || !value.canConvertToLong() ? null
                : Instant.ofEpochSecond(value.longValue());
    }

    private boolean isUncertainCurrentSession(Instant timestamp, ZoneId timezone, Instant regularSessionEnd, Instant fetchedAt) {
        ZoneId effectiveZone = timezone == null ? ZoneOffset.UTC : timezone;
        LocalDate candleDate = timestamp.atZone(effectiveZone).toLocalDate();
        LocalDate currentDate = fetchedAt.atZone(effectiveZone).toLocalDate();
        if (!candleDate.equals(currentDate)) {
            return false;
        }
        return regularSessionEnd == null || fetchedAt.isBefore(regularSessionEnd.plus(properties.getFreshness().getCloseGrace()));
    }

    private boolean retryable(MarketDataProviderException exception) {
        return exception.type() == ProviderFailureType.TIMEOUT
                || exception.type() == ProviderFailureType.THROTTLED
                || exception.type() == ProviderFailureType.PROVIDER_UNAVAILABLE;
    }

    private Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null) {
            return retryAfter.compareTo(properties.getRetry().getMaxBackoff()) > 0 ? properties.getRetry().getMaxBackoff() : retryAfter;
        }
        long base = properties.getRetry().getInitialBackoff().toMillis() * (1L << Math.min(attempt - 1, 10));
        long capped = Math.min(base, properties.getRetry().getMaxBackoff().toMillis());
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(Math.max(1, capped / 2), capped + 1));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(ProviderFailureType.TIMEOUT, PROVIDER_ID, "Yahoo retry interrupted", null, null, exception);
        }
    }

    private Duration retryAfter(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isEmpty()) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(header.get())));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MarketDataProviderException malformed(String message) {
        return new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, PROVIDER_ID, message);
    }
}
