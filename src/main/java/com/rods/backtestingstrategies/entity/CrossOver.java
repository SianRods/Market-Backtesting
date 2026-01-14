package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "crossovers",
        indexes = {
                @Index(name = "idx_crossover_date", columnList = "date"),
                @Index(name = "idx_crossover_type", columnList = "type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requirement
@AllArgsConstructor(access = AccessLevel.PRIVATE)  // force factory usage
@ToString
@EqualsAndHashCode(of = {"date", "type", "price"})
public class CrossOver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Date when crossover occurred
    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CrossOverType type; // BULLISH or BEARISH

    // Price at crossover
    @Column(nullable = false)
    private double price;

    /* ==========================
       Factory Methods
       ========================== */

    public static CrossOver bullish(Candle candle) {
        return new CrossOver(
                null,
                candle.getDate(),
                CrossOverType.BULLISH,
                candle.getClosePrice()
        );
    }

    public static CrossOver bearish(Candle candle) {
        return new CrossOver(
                null,
                candle.getDate(),
                CrossOverType.BEARISH,
                candle.getClosePrice()
        );
    }
}
