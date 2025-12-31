package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Fecthing the stock data from the database -> stocks are fetched in the ascending order
// depending on the
public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolOrderByDateAsc(String symbol);
}
