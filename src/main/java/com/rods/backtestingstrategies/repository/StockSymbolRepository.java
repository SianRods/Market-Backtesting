package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.symbol) LIKE LOWER(CONCAT(:query, '%'))
           OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY s.matchScore DESC
    """)
    List<StockSymbol> searchSymbols(@Param("query") String query);

    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.symbol) = LOWER(:symbol)
    """)
    StockSymbol findBySymbol(@Param("symbol") String symbol);

    @Query("""
        SELECT MAX(s.lastFetched)
        FROM StockSymbol s
        WHERE LOWER(s.symbol) LIKE LOWER(CONCAT(:query, '%'))
    """)
    LocalDateTime findLastFetchedForQuery(@Param("query") String query);
}
