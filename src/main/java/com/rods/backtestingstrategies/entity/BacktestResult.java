package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    private double initialCapital;
    private double finalCapital;
    private double profitLoss;
}
