package com.rods.backtestingstrategies.entity;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor

public class TradeSignal {
    private LocalDate date;  // decided date for given time

    @Enumerated(EnumType.STRING)      // store "BUY" / "SELL" in DB
    private SignalType type;  // deciding whether to enter or exit the market
    private  double price;

}
