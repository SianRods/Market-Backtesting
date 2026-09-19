package com.rods.backtestingstrategies.service;

import com.rods.backtestingstrategies.domain.Candle;
import com.rods.backtestingstrategies.marketdata.AdjustmentPolicy;
import com.rods.backtestingstrategies.marketdata.CandleNormalizer;
import com.rods.backtestingstrategies.marketdata.ConservativeTradingSessionCalendar;
import com.rods.backtestingstrategies.marketdata.DailyCandleResponse;
import com.rods.backtestingstrategies.marketdata.FreshnessStatus;
import com.rods.backtestingstrategies.marketdata.MarketDataFreshness;
import com.rods.backtestingstrategies.marketdata.MarketDataProperties;
import com.rods.backtestingstrategies.marketdata.MarketDataProvider;
import com.rods.backtestingstrategies.marketdata.MarketDataProviderException;
import com.rods.backtestingstrategies.marketdata.MarketDataSnapshot;
import com.rods.backtestingstrategies.marketdata.MarketQuote;
import com.rods.backtestingstrategies.marketdata.NormalizedCandle;
import com.rods.backtestingstrategies.marketdata.ProviderFailureType;
import com.rods.backtestingstrategies.marketdata.TradingSessionCalendar;
import com.rods.backtestingstrategies.repository.CandleRepository;
import com.rods.backtestingstrategies.repository.CandleIngestionRepository;
import com.rods.backtestingstrategies.repository.StockSymbolRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates provider sync, provenance-preserving persistence, and explicit data freshness. */
@Service
@Slf4j
public class MarketDataService {

    private static final AdjustmentPolicy DEFAULT_ADJUSTMENT_POLICY = AdjustmentPolicy.RAW_OHLC;
    private final MarketDataProvider provider;
    private final CandleRepository candleRepository;
    private final CandleIngestionRepository ingestionRepository;
    private final CandleNormalizer normalizer;
    private final StockSymbolRepository symbolRepository;
    private final TradingSessionCalendar tradingSessionCalendar;
    private final MarketDataProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, CompletableFuture<MarketDataSnapshot>> inFlight = new ConcurrentHashMap<>();

    public MarketDataService(
            MarketDataProvider provider,
            CandleRepository candleRepository,
            CandleIngestionRepository ingestionRepository,
            CandleNormalizer normalizer,
            StockSymbolRepository symbolRepository,
            ConservativeTradingSessionCalendar tradingSessionCalendar,
            MarketDataProperties properties,
            TransactionTemplate transactionTemplate) {
        this.provider = provider;
        this.candleRepository = candleRepository;
        this.ingestionRepository = ingestionRepository;
        this.normalizer = normalizer;
        this.symbolRepository = symbolRepository;
        this.tradingSessionCalendar = tradingSessionCalendar;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public MarketDataSnapshot getCandleSnapshot(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        List<com.rods.backtestingstrategies.entity.Candle> stored = stored(normalizedSymbol);
        MarketDataFreshness currentFreshness = freshness(stored, fallbackTimezone(normalizedSymbol), null, Instant.now());
        if (!stored.isEmpty() && currentFreshness.status() == FreshnessStatus.FRESH) {
            return snapshot(stored, currentFreshness);
        }
        String key = provider.metadata().providerId() + ':' + normalizedSymbol;
        CompletableFuture<MarketDataSnapshot> created = new CompletableFuture<>();
        CompletableFuture<MarketDataSnapshot> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            return existing.join();
        }
        try {
            MarketDataSnapshot synced = Objects.requireNonNull(transactionTemplate.execute(
                    status -> synchronize(normalizedSymbol)), "synchronization returned no result");
            created.complete(synced);
            return synced;
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, created);
        }
    }

    /** Existing callers receive validated domain candles; use getCandleSnapshot for freshness metadata. */
    public List<Candle> getCandles(String symbol) {
        return getCandleSnapshot(symbol).candles();
    }

    public MarketQuote getQuote(String symbol) {
        return provider.fetchQuote(normalizeSymbol(symbol));
    }

    public Page<com.rods.backtestingstrategies.entity.StockSymbol> searchSymbols(String query, String exchange, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return Page.empty(pageable);
        }
        return exchange == null || exchange.isBlank()
                ? symbolRepository.searchSymbolsPage(normalizedQuery, pageable)
                : symbolRepository.searchSymbolsByExchangePage(normalizedQuery, exchange.trim(), pageable);
    }

    public List<com.rods.backtestingstrategies.entity.StockSymbol> getSymbolsByExchange(String exchange) {
        return symbolRepository.findByExchange(exchange);
    }

    private MarketDataSnapshot synchronize(String symbol) {
        if (!ingestionRepository.tryAcquireSymbolLock(provider.metadata().providerId(), symbol)) {
            List<com.rods.backtestingstrategies.entity.Candle> concurrentData = stored(symbol);
            if (concurrentData.isEmpty()) {
                throw new MarketDataProviderException(ProviderFailureType.PROVIDER_UNAVAILABLE, provider.metadata().providerId(),
                        "Another instance is synchronizing this symbol and no prior dataset is available");
            }
            MarketDataFreshness freshness = freshness(concurrentData, fallbackTimezone(symbol), null, Instant.now());
            return snapshot(concurrentData, new MarketDataFreshness(FreshnessStatus.STALE_UNVERIFIED,
                    freshness.latestSessionDate(), freshness.checkedAt(), "DATABASE_LOCK_NOT_ACQUIRED", null));
        }
        List<com.rods.backtestingstrategies.entity.Candle> current = stored(symbol);
        LocalDate requestedEnd = LocalDate.now(ZoneId.of("UTC"));
        LocalDate requestedStart = current.isEmpty()
                ? requestedEnd.minusYears(5)
                : current.getLast().getDate().minusDays(properties.getFreshness().getCorrectionOverlapDays());
        try {
            DailyCandleResponse response = provider.fetchDailyCandles(symbol, requestedStart, requestedEnd, fallbackTimezone(symbol));
            List<NormalizedCandle> normalized = normalizer.normalize(response, DEFAULT_ADJUSTMENT_POLICY, fallbackTimezone(symbol));
            var batchId = ingestionRepository.startBatch(response, requestedStart, requestedEnd);
            ingestionRepository.upsert(response, normalized, DEFAULT_ADJUSTMENT_POLICY, requestedStart, requestedEnd, batchId);
            List<com.rods.backtestingstrategies.entity.Candle> refreshed = stored(symbol);
            return snapshot(refreshed, freshness(refreshed, response.exchangeTimezone(), response.regularSessionEnd(), Instant.now()));
        } catch (MarketDataProviderException exception) {
            ingestionRepository.recordFailedBatch(provider.metadata().providerId(), requestedStart, requestedEnd,
                    provider.metadata().adapterVersion(), exception.type().name(), exception.getMessage());
            if (current.isEmpty()) {
                throw exception;
            }
            MarketDataFreshness stale = freshness(current, fallbackTimezone(symbol), null, Instant.now());
            log.warn("Market-data refresh failed provider={} symbol={} type={}", provider.metadata().providerId(), symbol, exception.type());
            return snapshot(current, new MarketDataFreshness(FreshnessStatus.STALE_REFRESH_FAILED, stale.latestSessionDate(),
                    stale.checkedAt(), stale.calendarConfidence(), exception.type()));
        }
    }

    private List<com.rods.backtestingstrategies.entity.Candle> stored(String symbol) {
        return candleRepository.findByProviderAndSymbolOrderByDateAsc(provider.metadata().providerId(), symbol);
    }

    private MarketDataSnapshot snapshot(List<com.rods.backtestingstrategies.entity.Candle> persisted, MarketDataFreshness freshness) {
        List<Candle> domain = new ArrayList<>();
        for (com.rods.backtestingstrategies.entity.Candle candle : persisted) {
            if (candle.getAdjustmentPolicy() == AdjustmentPolicy.BACK_ADJUSTED_OHLC && candle.getAdjustmentFactor() == null) {
                throw new MarketDataProviderException(ProviderFailureType.MALFORMED_RESPONSE, candle.getProvider(),
                        "Persisted adjusted candle is missing its adjustment factor");
            }
            domain.add(new Candle(candle.getSymbol(), candle.getDate(),
                    domainPrice(candle.getRawOpen(), candle), domainPrice(candle.getRawHigh(), candle),
                    domainPrice(candle.getRawLow(), candle), domainPrice(candle.getRawClose(), candle), candle.getVolume()));
        }
        return new MarketDataSnapshot(domain, freshness);
    }

    private MarketDataFreshness freshness(
            List<com.rods.backtestingstrategies.entity.Candle> candles,
            ZoneId timezone,
            Instant regularSessionEnd,
            Instant now) {
        if (candles.isEmpty()) {
            return new MarketDataFreshness(FreshnessStatus.STALE_UNVERIFIED, null, now, "NO_DATA", null);
        }
        TradingSessionCalendar.SessionExpectation expected = tradingSessionCalendar.latestExpectedCompletedSession(timezone, now, regularSessionEnd);
        LocalDate latest = candles.getLast().getDate();
        FreshnessStatus status = latest.isBefore(expected.sessionDate()) ? FreshnessStatus.STALE_UNVERIFIED : FreshnessStatus.FRESH;
        return new MarketDataFreshness(status, latest, now, expected.confidence(), null);
    }

    private ZoneId fallbackTimezone(String symbol) {
        var stockSymbol = symbolRepository.findBySymbol(symbol);
        return stockSymbol == null ? null : parseTimezone(stockSymbol.getTimezone());
    }

    private java.math.BigDecimal domainPrice(java.math.BigDecimal raw, com.rods.backtestingstrategies.entity.Candle candle) {
        return candle.getAdjustmentPolicy() == AdjustmentPolicy.BACK_ADJUSTED_OHLC
                ? raw.multiply(candle.getAdjustmentFactor()).setScale(com.rods.backtestingstrategies.domain.Decimal.SCALE,
                        com.rods.backtestingstrategies.domain.Decimal.ROUNDING)
                : raw;
    }

    private ZoneId parseTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) return null;
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new MarketDataProviderException(ProviderFailureType.INVALID_SYMBOL, provider.metadata().providerId(), "symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
