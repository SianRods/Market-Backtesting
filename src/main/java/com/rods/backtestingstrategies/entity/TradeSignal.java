package com.rods.backtestingstrategies.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "trade_signals",
        indexes = {
                @Index(name = "idx_trade_signal_date", columnList = "signalDate"),
                @Index(name = "idx_trade_signal_type", columnList = "signalType")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requirement
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Force factory usage
@EqualsAndHashCode(of = {"signalDate", "signalType", "price"})
@ToString
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Date on which signal is generated
    @Column(nullable = false)
    private LocalDate signalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SignalType signalType;

    // Price at signal generation (usually close price)
    @Column(nullable = false)
    private double price;

    // Optional: identify which strategy generated this signal
    @Column(length = 100)
    private String strategyName;

    /* ==========================
       Factory Methods
       ========================== */

    public static TradeSignal buy(Candle candle) {
        return new TradeSignal(
                null,
                candle.getDate(),
                SignalType.BUY,
                candle.getClosePrice(),
                null
        );
    }

    public static TradeSignal sell(Candle candle) {
        return new TradeSignal(
                null,
                candle.getDate(),
                SignalType.SELL,
                candle.getClosePrice(),
                null
        );
    }

    public static TradeSignal hold() {
        return new TradeSignal(
                null,
                null,
                SignalType.HOLD,
                0.0,
                null
        );
    }

    /* ==========================
       Optional helpers
       ========================== */

    public TradeSignal withStrategyName(String strategyName) {
        this.strategyName = strategyName;
        return this;
    }
}
