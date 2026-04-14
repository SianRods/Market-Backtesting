package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.StockSymbol;
import com.rods.backtestingstrategies.repository.StockSymbolRepository;
import com.rods.backtestingstrategies.service.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/symbols")
@CrossOrigin
public class SymbolController {

    private final MarketDataService marketDataService;
    private final StockSymbolRepository symbolRepository;

    public SymbolController(MarketDataService marketDataService,
                            StockSymbolRepository symbolRepository) {
        this.marketDataService = marketDataService;
        this.symbolRepository = symbolRepository;
    }

    /**
     * Search symbols by keyword (matches symbol prefix or company name).
     * Optionally filter by exchange.
     */
    @GetMapping("/search")
    public List<StockSymbol> searchSymbols(
            @RequestParam String query,
            @RequestParam(required = false) String exchange
    ) {
        System.out.println("Symbol search request: query=" + query + ", exchange=" + exchange);

        if (query == null || query.length() < 1) {
            return List.of();
        }

        if (exchange != null && !exchange.isBlank()) {
            return marketDataService.searchSymbolsByExchange(query.trim(), exchange.trim());
        }

        return marketDataService.searchSymbols(query.trim());
    }

    /**
     * Get all symbols for a specific exchange.
     */
    @GetMapping("/exchange/{exchange}")
    public List<StockSymbol> getByExchange(@PathVariable String exchange) {
        return marketDataService.getSymbolsByExchange(exchange);
    }

    /**
     * Get symbols filtered by sector.
     */
    @GetMapping("/sector/{sector}")
    public List<StockSymbol> getBySector(@PathVariable String sector) {
        return symbolRepository.findBySector(sector);
    }

    /**
     * Get all available exchanges.
     */
    @GetMapping("/exchanges")
    public List<String> getExchanges() {
        return symbolRepository.findAllExchanges();
    }

    /**
     * Get all available sectors.
     */
    @GetMapping("/sectors")
    public List<String> getSectors() {
        return symbolRepository.findAllSectors();
    }

    /**
     * Get summary stats about the ticker database.
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return Map.of(
                "totalSymbols", symbolRepository.count(),
                "exchanges", symbolRepository.findAllExchanges(),
                "sectors", symbolRepository.findAllSectors()
        );
    }
}
