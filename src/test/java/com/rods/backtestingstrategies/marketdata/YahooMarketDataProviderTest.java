package com.rods.backtestingstrategies.marketdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooMarketDataProviderTest {

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void normalizesCompletedDailyChartDataWithAdjustedCloseAndTimezone() {
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(successBody())));

        DailyCandleResponse response = provider(1).fetchDailyCandles("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals("AAPL", response.symbol());
        assertEquals("America/New_York", response.exchangeTimezone().getId());
        assertEquals(2, response.candles().size());
        assertTrue(response.adjustedCloseAvailable());
        assertEquals("10.5", response.candles().getFirst().adjustedClose().stripTrailingZeros().toPlainString());
    }

    @Test
    void mapsThrottleWithRetryAfterWithoutCallingYahooRepeatedlyWhenConfiguredForOneAttempt() {
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")));

        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> provider(1).fetchDailyCandles("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));

        assertEquals(ProviderFailureType.THROTTLED, exception.type());
        assertEquals(Duration.ofSeconds(2), exception.retryAfter());
        server.verify(1, getRequestedFor(urlPathEqualTo("/v8/finance/chart/AAPL")));
    }

    @Test
    void rejectsMalformedArraysInsteadOfCreatingZeroValuedCandles() {
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(misalignedBody())));

        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> provider(1).fetchDailyCandles("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));

        assertEquals(ProviderFailureType.MALFORMED_RESPONSE, exception.type());
    }

    @Test
    void validatesSymbolsBeforeConstructingARequest() {
        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> provider(1).fetchDailyCandles("AAPL/../../bad", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));

        assertEquals(ProviderFailureType.INVALID_SYMBOL, exception.type());
        server.verify(0, getRequestedFor(urlPathEqualTo("/v8/finance/chart/AAPL")));
    }

    @Test
    void retriesATransientServerFailureThenReturnsValidatedData() {
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .inScenario("transient-yahoo-failure")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("recovered")
                .willReturn(aResponse().withStatus(503)));
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .inScenario("transient-yahoo-failure")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(successBody())));

        DailyCandleResponse response = provider(2).fetchDailyCandles("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals(2, response.candles().size());
        server.verify(2, getRequestedFor(urlPathEqualTo("/v8/finance/chart/AAPL")));
    }

    @Test
    void mapsOverallRequestTimeoutToTypedFailure() {
        server.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(aResponse().withFixedDelay(250).withBody(successBody())));

        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> provider(1, Duration.ofSeconds(1), Duration.ofMillis(50))
                        .fetchDailyCandles("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));

        assertEquals(ProviderFailureType.TIMEOUT, exception.type());
    }

    @Test
    void excludesCurrentSessionCandleUntilTheRegularSessionAndGracePeriodEnd() {
        Instant now = Instant.parse("2024-01-03T15:00:00Z");
        String body = """
                {"chart":{"result":[{"meta":{"symbol":"AAPL","exchangeTimezoneName":"America/New_York",
                "currentTradingPeriod":{"regular":{"end":%d}}},"timestamp":[1704205800,%d],
                "indicators":{"quote":[{"open":[10.0,11.0],"high":[11.0,12.0],"low":[9.0,10.0],"close":[10.0,11.0],"volume":[100,200]}]}}],"error":null}}
                """.formatted(now.plus(Duration.ofHours(2)).getEpochSecond(), now.getEpochSecond());

        DailyCandleResponse response = provider(1).parseChart("AAPL", body, now);

        assertEquals(1, response.candles().size());
        assertEquals(Instant.ofEpochSecond(1704205800), response.candles().getFirst().timestamp());
    }

    @Test
    void mapsChartErrorsToTypedFailureInsteadOfReturningAnEmptySuccess() {
        MarketDataProviderException exception = assertThrows(MarketDataProviderException.class,
                () -> provider(1).parseChart("INVALID", """
                        {"chart":{"result":null,"error":{"description":"No data found, symbol may be delisted"}}}
                        """, Instant.parse("2024-01-01T00:00:00Z")));

        assertEquals(ProviderFailureType.INVALID_SYMBOL, exception.type());
    }

    private YahooMarketDataProvider provider(int attempts) {
        return provider(attempts, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private YahooMarketDataProvider provider(int attempts, Duration responseTimeout, Duration requestTimeout) {
        MarketDataProperties properties = new MarketDataProperties();
        properties.getYahoo().setBaseUri(server.baseUrl());
        properties.getYahoo().setConnectTimeout(Duration.ofSeconds(1));
        properties.getYahoo().setResponseTimeout(responseTimeout);
        properties.getYahoo().setRequestTimeout(requestTimeout);
        properties.getRetry().setMaxAttempts(attempts);
        properties.getRetry().setInitialBackoff(Duration.ofMillis(1));
        properties.getRetry().setMaxBackoff(Duration.ofMillis(5));
        return new YahooMarketDataProvider(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), new ObjectMapper(),
                properties, CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
    }

    private String successBody() {
        return """
                {"chart":{"result":[{"meta":{"symbol":"AAPL","exchangeTimezoneName":"America/New_York","currency":"USD","instrumentType":"EQUITY"},
                "timestamp":[1704205800,1704292200],"indicators":{"quote":[{"open":[10.0,11.0],"high":[11.0,12.0],"low":[9.0,10.0],"close":[10.0,11.0],"volume":[100,200]}],
                "adjclose":[{"adjclose":[10.5,11.5]}]}}],"error":null}}
                """;
    }

    private String misalignedBody() {
        return """
                {"chart":{"result":[{"meta":{"symbol":"AAPL","exchangeTimezoneName":"America/New_York"},
                "timestamp":[1704205800,1704292200],"indicators":{"quote":[{"open":[10.0],"high":[11.0,12.0],"low":[9.0,10.0],"close":[10.0,11.0],"volume":[100,200]}]}}],"error":null}}
                """;
    }
}
