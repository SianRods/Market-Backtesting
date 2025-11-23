package com.rods.backtestingstrategies.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private String date;
    private String type; // "BUY" or "SELL"
    private double price;
    private long shares;
    private double cashAfter;
    private double equityAfter;

}
