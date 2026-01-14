package com.rods.backtestingstrategies.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StrategyFactory {


//    Finds all Strategy beans
//
//    Injects them as a List<Strategy>
//
//    Factory maps them by StrategyType


    private final Map<StrategyType, Strategy> strategies;

    public StrategyFactory(List<Strategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        Strategy::getType,
                        s -> s
                ));
    }

    public Strategy getStrategy(StrategyType type) {
        Strategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported strategy: " + type);
        }
        return strategy;
    }
}
