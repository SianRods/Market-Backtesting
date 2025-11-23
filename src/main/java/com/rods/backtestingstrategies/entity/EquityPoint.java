package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquityPoint {

    private String date;    // "YYYY-MM-DD"
    private double price;
    private double equity;
    private long shares;    // integer shares on that date
    private double cash;
}
