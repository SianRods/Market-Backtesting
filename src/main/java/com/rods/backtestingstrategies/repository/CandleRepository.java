package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolOrderByDateAsc(String symbol);
}
