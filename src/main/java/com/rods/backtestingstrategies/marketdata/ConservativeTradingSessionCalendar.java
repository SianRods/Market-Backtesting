package com.rods.backtestingstrategies.marketdata;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Weekday-only fallback. It intentionally reports reduced confidence for holidays and early closes. */
@Component
public class ConservativeTradingSessionCalendar implements TradingSessionCalendar {

    private final MarketDataProperties properties;

    public ConservativeTradingSessionCalendar(MarketDataProperties properties) {
        this.properties = properties;
    }

    @Override
    public SessionExpectation latestExpectedCompletedSession(ZoneId exchangeTimezone, Instant now, Instant regularSessionEnd) {
        ZoneId zone = exchangeTimezone == null ? ZoneId.of("UTC") : exchangeTimezone;
        LocalDate candidate = now.atZone(zone).toLocalDate();
        boolean beforeKnownClose = regularSessionEnd != null && now.isBefore(regularSessionEnd.plus(properties.getFreshness().getCloseGrace()));
        if (beforeKnownClose || regularSessionEnd == null) {
            candidate = candidate.minusDays(1);
        }
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }
        return new SessionExpectation(candidate, regularSessionEnd == null ? "CONSERVATIVE_NO_SESSION_METADATA" : "WEEKDAY_ONLY");
    }
}
