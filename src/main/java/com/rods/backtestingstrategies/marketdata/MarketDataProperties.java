package com.rods.backtestingstrategies.marketdata;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data")
public class MarketDataProperties {

    private final Yahoo yahoo = new Yahoo();
    private final Retry retry = new Retry();
    private final Freshness freshness = new Freshness();

    public Yahoo getYahoo() {
        return yahoo;
    }

    public Retry getRetry() {
        return retry;
    }

    public Freshness getFreshness() {
        return freshness;
    }

    public static class Yahoo {
        private String baseUri = "https://query1.finance.yahoo.com";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration responseTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(15);
        private String userAgent = "BacktestingStrategies/phase-2 educational-validation";

        public String getBaseUri() { return baseUri; }
        public void setBaseUri(String baseUri) { this.baseUri = baseUri; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getResponseTimeout() { return responseTimeout; }
        public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(250);
        private Duration maxBackoff = Duration.ofSeconds(3);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    }

    public static class Freshness {
        private Duration closeGrace = Duration.ofMinutes(20);
        private int correctionOverlapDays = 5;

        public Duration getCloseGrace() { return closeGrace; }
        public void setCloseGrace(Duration closeGrace) { this.closeGrace = closeGrace; }
        public int getCorrectionOverlapDays() { return correctionOverlapDays; }
        public void setCorrectionOverlapDays(int correctionOverlapDays) { this.correctionOverlapDays = correctionOverlapDays; }
    }
}
