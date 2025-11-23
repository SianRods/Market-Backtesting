package com.rods.backtestingstrategies.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossOver {

    private String date;
    private String type; // "bull" or "bear"
    private double price;

}
