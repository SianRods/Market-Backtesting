package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "candles",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"symbol", "date"})
        }
)
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // We are using this Candle Entity to store the data related to a Stock in the database "OHLVC"
    private String symbol;

    // Enforcing that the Symbol and the Dates are always unique
    private LocalDate date;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double closePrice;
    private long volume;


}
