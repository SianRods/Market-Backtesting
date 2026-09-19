package com.rods.backtestingstrategies.domain;

/** Typed strategy configuration; no stringly-typed engine parameters are accepted. */
public sealed interface StrategyConfig permits SmaConfig, RsiConfig, MacdConfig, BuyAndHoldConfig {

    StrategyType type();

    int warmupCandles();
}
