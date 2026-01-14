package com.rods.backtestingstrategies.controller;

import com.rods.backtestingstrategies.entity.StockSymbol;
import com.rods.backtestingstrategies.service.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/symbols")
public class SymbolController {

    private final MarketDataService marketDataService;

    public SymbolController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/search")
    public List<StockSymbol> searchSymbols(@RequestParam String query) {
        System.out.println("Request for Symbol Search Received at the endpoint");
        if (query == null || query.length() < 2) {
            // returning empty list if query length is too small
            return List.of();
        }

        return marketDataService.searchSymbols(query.trim());
    }
}
