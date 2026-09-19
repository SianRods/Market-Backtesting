package com.rods.backtestingstrategies.repository;

import com.rods.backtestingstrategies.entity.Candle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findByProviderAndSymbolOrderByDateAsc(String provider, String symbol);

    List<Candle> findByProviderAndSymbolAndDateBetweenOrderByDateAsc(
            String provider, String symbol, LocalDate start, LocalDate end);

    Optional<Candle> findTopByProviderAndSymbolOrderByDateDesc(String provider, String symbol);

    @Query("select c.date from Candle c where c.provider = :provider and c.symbol = :symbol order by c.date asc")
    List<LocalDate> findStoredDates(@Param("provider") String provider, @Param("symbol") String symbol);
}
