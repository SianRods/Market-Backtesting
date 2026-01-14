package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_tx_date", columnList = "date"),
                @Index(name = "idx_tx_type", columnList = "type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requirement
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force factory usage
@ToString
@EqualsAndHashCode(of = {"date", "type", "price", "shares"})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Execution date
    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SignalType type; // BUY or SELL

    // Execution price
    @Column(nullable = false)
    private double price;

    // Number of shares traded
    @Column(nullable = false)
    private long shares;

    // Cash remaining after execution
    @Column(nullable = false)
    private double cashAfter;

    // Total equity after execution
    @Column(nullable = false)
    private double equityAfter;

    /* ==========================
       Factory Methods
       ========================== */

    public static Transaction buy(
            Candle candle,
            double price,
            long shares,
            double cashAfter,
            double equityAfter
    ) {
        return new Transaction(
                null,
                candle.getDate(),
                SignalType.BUY,
                price,
                shares,
                cashAfter,
                equityAfter
        );
    }

    public static Transaction sell(
            Candle candle,
            double price,
            long shares,
            double cashAfter,
            double equityAfter
    ) {
        return new Transaction(
                null,
                candle.getDate(),
                SignalType.SELL,
                price,
                shares,
                cashAfter,
                equityAfter
        );
    }
}
