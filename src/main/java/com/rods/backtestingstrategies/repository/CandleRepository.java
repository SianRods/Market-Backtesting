package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;


public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolOrderByDateAsc(String symbol);


    // Fetching the existing date from the Database --> To check whether there is a need to syncMarket() Data
    // Before we used to fetch the entire data first and then check the last data -->  very slow (in efficient)
    @Query("SELECT c.date FROM Candle c WHERE c.symbol = :symbol")
    List<LocalDate> findExistingDates(@Param("symbol") String symbol);


}
