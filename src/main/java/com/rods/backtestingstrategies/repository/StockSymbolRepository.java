package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    /**
     * Fuzzy search by symbol prefix or company name substring.
     * Ordered by match score descending.
     */
    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.symbol) LIKE LOWER(CONCAT(:query, '%'))
           OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY s.matchScore DESC
    """)
    List<StockSymbol> searchSymbols(@Param("query") String query);

    /**
     * Search symbols filtered by a specific exchange.
     */
    @Query("""
        SELECT s FROM StockSymbol s
        WHERE (LOWER(s.symbol) LIKE LOWER(CONCAT(:query, '%'))
           OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')))
           AND LOWER(s.exchange) = LOWER(:exchange)
        ORDER BY s.matchScore DESC
    """)
    List<StockSymbol> searchSymbolsByExchange(
            @Param("query") String query,
            @Param("exchange") String exchange
    );

    /**
     * Find a specific symbol (case-insensitive)
     */
    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.symbol) = LOWER(:symbol)
    """)
    StockSymbol findBySymbol(@Param("symbol") String symbol);

    /**
     * Get all symbols for a specific exchange
     */
    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.exchange) = LOWER(:exchange)
        ORDER BY s.symbol ASC
    """)
    List<StockSymbol> findByExchange(@Param("exchange") String exchange);

    /**
     * Get all symbols for a specific sector
     */
    @Query("""
        SELECT s FROM StockSymbol s
        WHERE LOWER(s.sector) = LOWER(:sector)
        ORDER BY s.symbol ASC
    """)
    List<StockSymbol> findBySector(@Param("sector") String sector);

    /**
     * Get the last fetched timestamp for symbol search freshness check
     */
    @Query("""
        SELECT MAX(s.lastFetched)
        FROM StockSymbol s
        WHERE LOWER(s.symbol) LIKE LOWER(CONCAT(:query, '%'))
    """)
    LocalDateTime findLastFetchedForQuery(@Param("query") String query);

    /**
     * Get all distinct exchange names
     */
    @Query("SELECT DISTINCT s.exchange FROM StockSymbol s ORDER BY s.exchange")
    List<String> findAllExchanges();

    /**
     * Get all distinct sectors
     */
    @Query("SELECT DISTINCT s.sector FROM StockSymbol s WHERE s.sector IS NOT NULL ORDER BY s.sector")
    List<String> findAllSectors();
}
