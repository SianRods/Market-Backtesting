package com.rods.backtestingstrategies.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestEngineTest {

    private final BacktestEngine engine = new BacktestEngine();

    @Test
    void indicatorsUseStandardSeedsAndLinearSeries() {
        List<Candle> candles = candles("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

        assertEquals(Arrays.asList(null, null, amount("2"), amount("3"), amount("4")), IndicatorCalculator.sma(candles.subList(0, 5), 3));
        assertEquals(Arrays.asList(null, null, amount("2"), amount("3"), amount("4")), IndicatorCalculator.ema(candles.subList(0, 5), 3));

        IndicatorCalculator.MacdSeries macd = IndicatorCalculator.macd(candles, 2, 3, 2);
        assertEquals(amount("0.5"), macd.macd().get(2));
        assertEquals(amount("0.5"), macd.signal().get(3));

        List<Candle> gainsOnly = candles("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
        assertEquals(amount("100"), IndicatorCalculator.wilderRsi(gainsOnly, 14).get(14));
    }

    @Test
    void decisionAtCloseExecutesOnlyAtTheNextOpen() {
        List<Candle> candles = candles("10", "11", "12");

        BacktestResult result = engine.run(candles, new BuyAndHoldConfig(), amount("100"));

        Fill fill = result.fills().getFirst();
        assertEquals(candles.get(0).date(), fill.signalDate());
        assertEquals(candles.get(1).date(), fill.executionDate());
        assertEquals(amount("11"), fill.requestedPrice());
        assertEquals(amount("9"), fill.quantity());
        assertEquals(amount("1"), fill.cashAfter());
        assertEquals(amount("9"), result.unrealizedPnl());
    }

    @Test
    void appliesCostsBeforeQuantityAndRetainsAuditableFillValues() {
        ExecutionModel costs = new ExecutionModel(true, amount("1"), new BigDecimal("0.001"), amount("100"), 1,
                FinalPositionPolicy.MARK_TO_MARKET);

        BacktestResult result = engine.run(candles("10", "10", "10"), new BuyAndHoldConfig(), amount("100"), costs, 0);

        Fill fill = result.fills().getFirst();
        assertEquals(amount("10.1"), fill.fillPrice());
        assertEquals(amount("9"), fill.quantity());
        assertEquals(amount("1.0909"), fill.fees());
        assertEquals(amount("8.0091"), fill.cashAfter());
        assertEquals(amount("1.0909"), result.metrics().totalFees());
    }

    @Test
    void finalCandleSignalIsReportedWithoutLookAheadFill() {
        BacktestResult result = engine.run(candles("3", "2", "4"), new SmaConfig(1, 2), amount("100"));

        assertTrue(result.fills().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.startsWith("Unfilled BUY signal")));
    }

    @Test
    void separatesRealizedAndUnrealizedPnlAndSupportsForcedLiquidation() {
        List<Candle> candles = candles("10", "11", "13");
        BacktestResult marked = engine.run(candles, new BuyAndHoldConfig(), amount("100"));
        assertEquals(Decimal.ZERO, marked.realizedPnl());
        assertEquals(amount("18"), marked.unrealizedPnl());
        assertEquals(1, marked.metrics().openPositions());
        assertEquals(0, marked.metrics().completedTrades());

        ExecutionModel liquidation = new ExecutionModel(true, Decimal.ZERO, Decimal.ZERO, Decimal.ZERO, 1,
                FinalPositionPolicy.LIQUIDATE_AT_LAST_CLOSE);
        BacktestResult liquidated = engine.run(candles, new BuyAndHoldConfig(), amount("100"), liquidation, 0);
        Fill finalFill = liquidated.fills().getLast();
        assertTrue(finalFill.forcedLiquidation());
        assertEquals(amount("18"), liquidated.realizedPnl());
        assertEquals(Decimal.ZERO, liquidated.unrealizedPnl());
        assertEquals(1, liquidated.metrics().completedTrades());
    }

    @Test
    void recordsCompletedRoundTripsAndUndefinedMetricsWithoutInfinity() {
        List<Candle> candles = List.of(
                candle(0, "3", "3"), candle(1, "2", "2"), candle(2, "4", "4"),
                candle(3, "5", "1"), candle(4, "4", "1"));

        BacktestResult result = engine.run(candles, new SmaConfig(1, 2), amount("100"));

        assertEquals(2, result.fills().size());
        assertEquals(amount("-20"), result.realizedPnl());
        assertEquals(1, result.metrics().completedTrades());
        assertEquals(1, result.metrics().losingTrades());
        assertEquals(amount("1"), result.metrics().averageHoldingDays());
        assertNull(result.metrics().winLossRatio());
        assertEquals(Decimal.ZERO, result.metrics().profitFactor());
        assertFalse(result.metrics().warnings().isEmpty());
    }

    @Test
    void flatEquityHasNullableSharpeAndNoNanOrInfinity() {
        BacktestResult result = engine.run(candles("10", "10", "10"), new BuyAndHoldConfig(), amount("100"));

        assertNull(result.metrics().sharpeRatio());
        assertNull(result.metrics().profitFactor());
        assertTrue(result.metrics().warnings().stream().anyMatch(warning -> warning.contains("Sharpe ratio is undefined")));
    }

    @Test
    void computesGoldenReturnCagrSharpeAndDrawdownFromEquityPoints() {
        List<Candle> candles = List.of(
                candle(0, "10", "10"), candle(1, "10", "10"), candle(2, "20", "20"), candle(3, "10", "10"));

        BacktestResult result = engine.run(candles, new BuyAndHoldConfig(), amount("100"));

        assertEquals(Decimal.ZERO, result.metrics().totalReturnPercent());
        assertEquals(Decimal.ZERO, result.metrics().cagrPercent());
        assertEquals(amount("50"), result.metrics().maxDrawdownPercent());
        assertEquals(3.4641016d, result.metrics().sharpeRatio().doubleValue(), 0.000001d);
    }

    @Test
    void rejectsInsufficientCapitalInvalidLotsAndSellsWithoutHoldingsWhilePreservingEquityInvariant() {
        BacktestResult insufficientCapital = engine.run(candles("10", "10", "10"), new BuyAndHoldConfig(), amount("5"));
        assertTrue(insufficientCapital.fills().isEmpty());
        assertTrue(insufficientCapital.warnings().stream().anyMatch(warning -> warning.contains("insufficient capital")));

        ExecutionModel lotTen = new ExecutionModel(true, Decimal.ZERO, Decimal.ZERO, Decimal.ZERO, 10,
                FinalPositionPolicy.MARK_TO_MARKET);
        BacktestResult invalidLot = engine.run(candles("10", "10", "10"), new BuyAndHoldConfig(), amount("95"), lotTen, 0);
        assertTrue(invalidLot.fills().isEmpty());

        BacktestResult missingHolding = engine.run(candles("3", "4", "1", "1"), new SmaConfig(1, 2), amount("100"));
        assertTrue(missingHolding.warnings().stream().anyMatch(warning -> warning.contains("no long position")));

        for (EquityPoint point : insufficientCapital.equityCurve()) {
            assertEquals(0, point.cash().add(point.quantity().multiply(point.markPrice())).compareTo(point.equity()));
        }
    }

    @Test
    void appliesSlippageAgainstBothBuyAndSellDirections() {
        ExecutionModel slippage = new ExecutionModel(true, Decimal.ZERO, Decimal.ZERO, amount("100"), 1,
                FinalPositionPolicy.MARK_TO_MARKET);
        List<Candle> candles = List.of(
                candle(0, "3", "3"), candle(1, "2", "2"), candle(2, "4", "4"),
                candle(3, "5", "1"), candle(4, "4", "1"));

        BacktestResult result = engine.run(candles, new SmaConfig(1, 2), amount("100"), slippage, 0);

        assertEquals(amount("5.05"), result.fills().getFirst().fillPrice());
        assertEquals(amount("3.96"), result.fills().getLast().fillPrice());
    }

    @Test
    void rejectsInvalidHistoryAndInvalidDomainValues() {
        assertThrows(DomainValidationException.class, () -> engine.run(List.of(), new BuyAndHoldConfig(), amount("100")));
        assertThrows(DomainValidationException.class,
                () -> engine.run(candles("10"), new BuyAndHoldConfig(), amount("100")));
        assertThrows(DomainValidationException.class,
                () -> new Candle("ABC", LocalDate.of(2024, 1, 1), amount("10"), amount("9"), amount("8"), amount("10"), 1));
        assertThrows(DomainValidationException.class, () -> new SmaConfig(5, 5));
    }

    @Test
    void processesLargeFixtureWithoutRepeatedIndicatorEvaluation() {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 20_000; index++) {
            candles.add(candle(index, String.valueOf(100 + index % 17), String.valueOf(100 + index % 17)));
        }

        List<StrategyEvent> events = new StrategyEvaluator().evaluate(candles, new MacdConfig(12, 26, 9));

        assertEquals(candles.size(), events.size());
    }

    private static List<Candle> candles(String... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            candles.add(candle(index, closes[index], closes[index]));
        }
        return candles;
    }

    private static Candle candle(int index, String open, String close) {
        BigDecimal openValue = amount(open);
        BigDecimal closeValue = amount(close);
        return new Candle("ABC", LocalDate.of(2024, 1, 1).plusDays(index), openValue,
                openValue.max(closeValue).add(BigDecimal.ONE), openValue.min(closeValue).divide(new BigDecimal("2")), closeValue, 100L);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(Decimal.SCALE, Decimal.ROUNDING);
    }
}
