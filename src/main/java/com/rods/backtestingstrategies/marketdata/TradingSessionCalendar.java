package com.rods.backtestingstrategies.marketdata;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Separate port because provider metadata is not a complete historical exchange calendar. */
public interface TradingSessionCalendar {

    SessionExpectation latestExpectedCompletedSession(ZoneId exchangeTimezone, Instant now, Instant regularSessionEnd);

    record SessionExpectation(LocalDate sessionDate, String confidence) {}
}
