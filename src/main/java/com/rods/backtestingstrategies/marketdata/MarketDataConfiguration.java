package com.rods.backtestingstrategies.marketdata;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfiguration {

    @Bean
    HttpClient yahooHttpClient(MarketDataProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getYahoo().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    CircuitBreakerRegistry marketDataCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    BulkheadRegistry marketDataBulkheadRegistry() {
        return BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(8).maxWaitDuration(Duration.ZERO).build());
    }
}
