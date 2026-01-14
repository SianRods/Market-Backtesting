package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "equity_points",
        indexes = {
                @Index(name = "idx_equity_point_date", columnList = "date")
        }
)
@Getter
@NoArgsConstructor // JPA requirement
@AllArgsConstructor // force factory usage
@ToString
@EqualsAndHashCode(of = {"date", "equity", "shares", "cash"})
public class EquityPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Date of this equity snapshot
    @Column(nullable = false)
    private LocalDate date;

    // Market price at this candle
    @Column(nullable = false)
    private double price;

    // Total portfolio equity (cash + holdings)
    @Column(nullable = false)
    private double equity;

    // Number of shares held
    @Column(nullable = false)
    private long shares;

    // Cash balance
    @Column(nullable = false)
    private double cash;



    /* ==========================
       Factory Method
       ========================== */

    public static EquityPoint of(
            Candle candle,
            double equity,
            long shares,
            double cash
    ) {
        return new EquityPoint(
                null,
                candle.getDate(),
                candle.getClosePrice(),
                equity,
                shares,
                cash
        );
    }
}
