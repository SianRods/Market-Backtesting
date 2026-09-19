package com.rods.backtestingstrategies.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic long-only backtest engine. Decisions use a completed close and fills occur only at
 * the following session open.
 */
public final class BacktestEngine {

    private final StrategyEvaluator strategyEvaluator;

    public BacktestEngine() {
        this(new StrategyEvaluator());
    }

    public BacktestEngine(StrategyEvaluator strategyEvaluator) {
        this.strategyEvaluator = strategyEvaluator;
    }

    public BacktestResult run(List<Candle> candles, StrategyConfig strategyConfig, BigDecimal initialCapital) {
        return run(candles, strategyConfig, initialCapital, ExecutionModel.defaults(), 0);
    }

    public BacktestResult run(
            List<Candle> candles,
            StrategyConfig strategyConfig,
            BigDecimal initialCapital,
            ExecutionModel executionModel,
            int measurementStartIndex) {
        validateInputs(candles, strategyConfig, initialCapital, executionModel, measurementStartIndex);
        List<Candle> ordered = List.copyOf(candles);
        List<StrategyEvent> events = strategyEvaluator.evaluate(ordered, strategyConfig);
        List<String> warnings = new ArrayList<>();
        List<Fill> fills = new ArrayList<>();
        List<CompletedTrade> completedTrades = new ArrayList<>();
        List<EquityPoint> curve = new ArrayList<>();

        BigDecimal cash = Decimal.value(initialCapital, "initialCapital");
        BigDecimal quantity = Decimal.ZERO;
        BigDecimal entryCostBasis = Decimal.ZERO;
        BigDecimal totalFees = Decimal.ZERO;
        BigDecimal totalTurnover = Decimal.ZERO;
        BigDecimal realizedPnl = Decimal.ZERO;
        PendingOrder pending = null;
        Entry entry = null;

        for (int index = 0; index < ordered.size(); index++) {
            Candle candle = ordered.get(index);
            if (pending != null && index >= measurementStartIndex) {
                Execution execution = execute(pending, candle, cash, quantity, entryCostBasis, executionModel);
                if (execution.warning() != null) {
                    warnings.add(execution.warning());
                } else {
                    cash = execution.cash();
                    quantity = execution.quantity();
                    entryCostBasis = execution.entryCostBasis();
                    totalFees = totalFees.add(execution.fill().fees());
                    totalTurnover = totalTurnover.add(execution.fill().fillPrice().multiply(execution.fill().quantity()));
                    fills.add(execution.fill());
                    if (execution.fill().action() == SignalAction.BUY) {
                        entry = new Entry(execution.fill().executionDate(), execution.fill().quantity(), entryCostBasis);
                    } else {
                        BigDecimal tradePnl = execution.realizedPnl();
                        realizedPnl = realizedPnl.add(tradePnl);
                        completedTrades.add(new CompletedTrade(
                                entry.date(), execution.fill().executionDate(), entry.quantity(), tradePnl,
                                ChronoUnit.DAYS.between(entry.date(), execution.fill().executionDate())));
                        entry = null;
                    }
                }
                pending = null;
            }

            StrategyEvent event = events.get(index);
            if (event.action() != SignalAction.HOLD && index >= measurementStartIndex) {
                if (index == ordered.size() - 1) {
                    warnings.add("Unfilled " + event.action() + " signal on final candle " + event.signalDate());
                } else {
                    pending = new PendingOrder(event);
                }
            }

            BigDecimal equity = cash.add(quantity.multiply(candle.close())).setScale(Decimal.SCALE, Decimal.ROUNDING);
            if (index >= measurementStartIndex) {
                curve.add(new EquityPoint(candle.date(), candle.close(), equity, quantity, cash));
            }
        }

        Candle lastCandle = ordered.getLast();
        if (quantity.signum() > 0 && executionModel.finalPositionPolicy() == FinalPositionPolicy.LIQUIDATE_AT_LAST_CLOSE) {
            StrategyEvent forcedEvent = new StrategyEvent(SignalAction.SELL, lastCandle.date(), lastCandle.close(), strategyConfig.type(), java.util.Map.of());
            Execution forced = execute(new PendingOrder(forcedEvent, true, lastCandle.close()), lastCandle, cash, quantity, entryCostBasis, executionModel);
            if (forced.warning() != null) {
                throw new DomainValidationException("forced final liquidation failed: " + forced.warning());
            }
            cash = forced.cash();
            totalFees = totalFees.add(forced.fill().fees());
            totalTurnover = totalTurnover.add(forced.fill().fillPrice().multiply(forced.fill().quantity()));
            realizedPnl = realizedPnl.add(forced.realizedPnl());
            completedTrades.add(new CompletedTrade(entry.date(), lastCandle.date(), entry.quantity(), forced.realizedPnl(),
                    ChronoUnit.DAYS.between(entry.date(), lastCandle.date())));
            fills.add(forced.fill());
            quantity = Decimal.ZERO;
            entryCostBasis = Decimal.ZERO;
            curve.set(curve.size() - 1, new EquityPoint(lastCandle.date(), lastCandle.close(), cash, quantity, cash));
        }

        BigDecimal finalCapital = curve.getLast().equity();
        BigDecimal unrealizedPnl = quantity.signum() == 0 ? Decimal.ZERO
                : quantity.multiply(lastCandle.close()).subtract(entryCostBasis).setScale(Decimal.SCALE, Decimal.ROUNDING);
        BigDecimal totalPnl = finalCapital.subtract(initialCapital).setScale(Decimal.SCALE, Decimal.ROUNDING);
        PerformanceMetrics metrics = calculateMetrics(curve, initialCapital, realizedPnl, unrealizedPnl, totalFees,
                totalTurnover, completedTrades, quantity.signum() > 0 ? 1 : 0, warnings);
        return new BacktestResult(ordered.getFirst().symbol(), strategyConfig, executionModel, Decimal.value(initialCapital, "initialCapital"),
                finalCapital, realizedPnl, unrealizedPnl, totalPnl, Decimal.percentage(Decimal.divide(totalPnl, initialCapital)),
                curve, events, fills, completedTrades, warnings, metrics);
    }

    private Execution execute(
            PendingOrder order,
            Candle candle,
            BigDecimal cash,
            BigDecimal currentQuantity,
            BigDecimal entryCostBasis,
            ExecutionModel model) {
        StrategyEvent event = order.event();
        if (event.action() == SignalAction.BUY) {
            if (currentQuantity.signum() > 0) {
                return Execution.warning("Rejected duplicate long-only BUY signal on " + event.signalDate());
            }
            BigDecimal requested = order.forcedPrice() == null ? candle.open() : order.forcedPrice();
            BigDecimal fillPrice = model.buyFillPrice(requested);
            BigDecimal rawAffordable = Decimal.divide(cash.subtract(model.fixedCommission()),
                    fillPrice.multiply(Decimal.ONE.add(model.commissionRate())));
            BigDecimal quantity = affordableQuantity(rawAffordable, model);
            if (quantity.signum() <= 0) {
                return Execution.warning("Rejected BUY on " + candle.date() + ": insufficient capital after costs");
            }
            BigDecimal notional = fillPrice.multiply(quantity);
            BigDecimal fees = model.fees(notional);
            BigDecimal newCash = cash.subtract(notional).subtract(fees).setScale(Decimal.SCALE, Decimal.ROUNDING);
            if (newCash.signum() < 0) {
                return Execution.warning("Rejected BUY on " + candle.date() + ": costs exceed available cash");
            }
            BigDecimal newCostBasis = notional.add(fees).setScale(Decimal.SCALE, Decimal.ROUNDING);
            BigDecimal equityAfter = newCash.add(quantity.multiply(candle.close())).setScale(Decimal.SCALE, Decimal.ROUNDING);
            return Execution.success(newCash, quantity, newCostBasis,
                    new Fill(SignalAction.BUY, event.signalDate(), candle.date(), requested, fillPrice, quantity, fees,
                            newCash, quantity, equityAfter, order.forcedLiquidation()), Decimal.ZERO);
        }
        if (event.action() == SignalAction.SELL) {
            if (currentQuantity.signum() <= 0) {
                return Execution.warning("Rejected SELL on " + candle.date() + ": no long position is held");
            }
            BigDecimal requested = order.forcedPrice() == null ? candle.open() : order.forcedPrice();
            BigDecimal fillPrice = model.sellFillPrice(requested);
            BigDecimal notional = fillPrice.multiply(currentQuantity);
            BigDecimal fees = model.fees(notional);
            BigDecimal proceeds = notional.subtract(fees).setScale(Decimal.SCALE, Decimal.ROUNDING);
            BigDecimal newCash = cash.add(proceeds).setScale(Decimal.SCALE, Decimal.ROUNDING);
            BigDecimal realized = proceeds.subtract(entryCostBasis).setScale(Decimal.SCALE, Decimal.ROUNDING);
            return Execution.success(newCash, Decimal.ZERO, Decimal.ZERO,
                    new Fill(SignalAction.SELL, event.signalDate(), candle.date(), requested, fillPrice, currentQuantity, fees,
                            newCash, Decimal.ZERO, newCash, order.forcedLiquidation()), realized);
        }
        return Execution.warning("HOLD cannot be executed");
    }

    private BigDecimal affordableQuantity(BigDecimal rawAffordable, ExecutionModel model) {
        if (rawAffordable.signum() <= 0) {
            return Decimal.ZERO;
        }
        if (!model.integerSharesOnly()) {
            return rawAffordable.setScale(Decimal.SCALE, Decimal.ROUNDING);
        }
        BigDecimal rounded = rawAffordable.setScale(0, RoundingMode.DOWN);
        BigDecimal lot = BigDecimal.valueOf(model.minimumLot());
        return rounded.divide(lot, 0, RoundingMode.DOWN).multiply(lot).setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    private PerformanceMetrics calculateMetrics(
            List<EquityPoint> curve,
            BigDecimal initialCapital,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalFees,
            BigDecimal turnover,
            List<CompletedTrade> completedTrades,
            int openPositions,
            List<String> warnings) {
        BigDecimal finalEquity = curve.getLast().equity();
        BigDecimal totalReturn = Decimal.divide(finalEquity.subtract(initialCapital), initialCapital);
        BigDecimal cagr = cagr(curve, initialCapital, finalEquity, warnings);
        BigDecimal sharpe = sharpe(curve, warnings);
        BigDecimal drawdown = maxDrawdown(curve);
        int wins = (int) completedTrades.stream().filter(trade -> trade.realizedPnl().signum() > 0).count();
        int losses = (int) completedTrades.stream().filter(trade -> trade.realizedPnl().signum() < 0).count();
        int breakeven = completedTrades.size() - wins - losses;
        BigDecimal grossProfit = completedTrades.stream().map(CompletedTrade::realizedPnl).filter(value -> value.signum() > 0)
                .reduce(Decimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = completedTrades.stream().map(CompletedTrade::realizedPnl).filter(value -> value.signum() < 0)
                .map(BigDecimal::abs).reduce(Decimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null : Decimal.divide(grossProfit, grossLoss);
        if (profitFactor == null) {
            warnings.add("Profit factor is undefined because there are no losing completed trades");
        }
        BigDecimal averageWin = completedTrades.stream().map(CompletedTrade::realizedPnl).filter(value -> value.signum() > 0)
                .reduce(Decimal.ZERO, BigDecimal::add);
        if (wins > 0) {
            averageWin = Decimal.divide(averageWin, BigDecimal.valueOf(wins));
        }
        BigDecimal averageLoss = grossLoss;
        if (losses > 0) {
            averageLoss = Decimal.divide(averageLoss, BigDecimal.valueOf(losses));
        }
        BigDecimal winLoss = wins == 0 || losses == 0 ? null : Decimal.divide(averageWin, averageLoss);
        if (winLoss == null) {
            warnings.add("Win/loss ratio is undefined because completed wins or losses are absent");
        }
        BigDecimal averageHolding = completedTrades.isEmpty() ? null
                : Decimal.divide(BigDecimal.valueOf(completedTrades.stream().mapToLong(CompletedTrade::holdingDays).sum()),
                        BigDecimal.valueOf(completedTrades.size()));
        return new PerformanceMetrics(Decimal.percentage(totalReturn), cagr == null ? null : Decimal.percentage(cagr), sharpe,
                Decimal.percentage(drawdown), profitFactor, winLoss, realizedPnl, unrealizedPnl, totalFees, turnover,
                completedTrades.size(), openPositions, wins, losses, breakeven, averageHolding, warnings);
    }

    private BigDecimal cagr(List<EquityPoint> curve, BigDecimal initialCapital, BigDecimal finalEquity, List<String> warnings) {
        long days = ChronoUnit.DAYS.between(curve.getFirst().date(), curve.getLast().date());
        if (days <= 0 || finalEquity.signum() <= 0) {
            warnings.add("CAGR is undefined because the measurement interval is zero days or final equity is non-positive");
            return null;
        }
        double years = days / 365.25d;
        double ratio = Decimal.divide(finalEquity, initialCapital).doubleValue();
        return BigDecimal.valueOf(Math.pow(ratio, 1d / years) - 1d).setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    private BigDecimal sharpe(List<EquityPoint> curve, List<String> warnings) {
        if (curve.size() < 3) {
            warnings.add("Sharpe ratio is undefined because fewer than two daily returns are available");
            return null;
        }
        List<BigDecimal> returns = new ArrayList<>();
        for (int index = 1; index < curve.size(); index++) {
            BigDecimal previous = curve.get(index - 1).equity();
            if (previous.signum() <= 0) {
                warnings.add("Sharpe ratio is undefined because equity is non-positive");
                return null;
            }
            returns.add(Decimal.divide(curve.get(index).equity().subtract(previous), previous));
        }
        BigDecimal mean = returns.stream().reduce(Decimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), Decimal.SCALE, Decimal.ROUNDING);
        BigDecimal sumSquares = returns.stream().map(value -> value.subtract(mean).pow(2)).reduce(Decimal.ZERO, BigDecimal::add);
        BigDecimal sampleVariance = sumSquares.divide(BigDecimal.valueOf(returns.size() - 1L), Decimal.SCALE, Decimal.ROUNDING);
        double deviation = Math.sqrt(sampleVariance.doubleValue());
        if (deviation == 0d) {
            warnings.add("Sharpe ratio is undefined because daily-return sample deviation is zero");
            return null;
        }
        return BigDecimal.valueOf(mean.doubleValue() / deviation * Math.sqrt(252d)).setScale(Decimal.SCALE, Decimal.ROUNDING);
    }

    private BigDecimal maxDrawdown(List<EquityPoint> curve) {
        BigDecimal peak = curve.getFirst().equity();
        BigDecimal maximum = Decimal.ZERO;
        for (EquityPoint point : curve) {
            if (point.equity().compareTo(peak) > 0) {
                peak = point.equity();
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = Decimal.divide(peak.subtract(point.equity()), peak);
                maximum = maximum.max(drawdown);
            }
        }
        return maximum;
    }

    private void validateInputs(List<Candle> candles, StrategyConfig config, BigDecimal capital, ExecutionModel model, int measurementStartIndex) {
        if (candles == null || candles.size() < 2) {
            throw new DomainValidationException("at least two candles are required for next-bar execution");
        }
        if (config == null || model == null) {
            throw new DomainValidationException("strategy configuration and execution model are required");
        }
        if (capital == null || capital.signum() <= 0) {
            throw new DomainValidationException("initialCapital must be positive");
        }
        if (measurementStartIndex < 0 || measurementStartIndex >= candles.size() - 1) {
            throw new DomainValidationException("measurementStartIndex must leave at least two candles");
        }
        for (int index = 1; index < candles.size(); index++) {
            if (!candles.get(index).symbol().equals(candles.getFirst().symbol())
                    || !candles.get(index).date().isAfter(candles.get(index - 1).date())) {
                throw new DomainValidationException("candles must be one symbol ordered by unique ascending date");
            }
        }
    }

    private record PendingOrder(StrategyEvent event, boolean forcedLiquidation, BigDecimal forcedPrice) {
        private PendingOrder(StrategyEvent event) {
            this(event, false, null);
        }
    }

    private record Entry(java.time.LocalDate date, BigDecimal quantity, BigDecimal costBasis) {}

    private record Execution(BigDecimal cash, BigDecimal quantity, BigDecimal entryCostBasis, Fill fill, BigDecimal realizedPnl, String warning) {
        private static Execution success(BigDecimal cash, BigDecimal quantity, BigDecimal entryCostBasis, Fill fill, BigDecimal realizedPnl) {
            return new Execution(cash, quantity, entryCostBasis, fill, realizedPnl, null);
        }

        private static Execution warning(String warning) {
            return new Execution(null, null, null, null, null, warning);
        }
    }
}
