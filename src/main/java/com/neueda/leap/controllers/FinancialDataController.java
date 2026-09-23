package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PriceHistoryDTO;
import com.neueda.leap.model.PriceHistory;
import com.neueda.leap.services.YahooFinanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for financial data endpoints.
 * 
 * Provides endpoints for fetching, storing, and analyzing financial data
 * from Yahoo Finance.
 */
@RestController
@RequestMapping("/api/v1/financial-data")
public class FinancialDataController {
    
    private final YahooFinanceService yahooFinanceService;
    
    public FinancialDataController(YahooFinanceService yahooFinanceService) {
        this.yahooFinanceService = yahooFinanceService;
    }
    
    /**
     * Fetch and store historical price data from Yahoo Finance.
     * 
     * GET /api/v1/financial-data/fetch?symbol=AAPL&startDate=2023-01-01&endDate=2024-01-01
     * 
     * @param symbol Stock symbol (e.g., "AAPL")
     * @param startDate Start date in format YYYY-MM-DD
     * @param endDate End date in format YYYY-MM-DD
     * @return Response with number of records stored
     */
    @GetMapping("/fetch")
    public ResponseEntity<?> fetchHistoricalData(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            List<PriceHistory> data = yahooFinanceService.fetchAndStoreHistoricalData(symbol, startDate, endDate);
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol,
                    "recordsStored", data.size(),
                    "startDate", startDate,
                    "endDate", endDate
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get current price for a symbol.
     * 
     * GET /api/v1/financial-data/current-price?symbol=AAPL
     * 
     * @param symbol Stock symbol
     * @return Current price information
     */
    @GetMapping("/current-price")
    public ResponseEntity<?> getCurrentPrice(@RequestParam String symbol) {
        return yahooFinanceService.getCurrentPrice(symbol)
                .map(price -> ResponseEntity.ok(Map.of(
                        "symbol", symbol,
                        "price", price,
                        "timestamp", LocalDate.now()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Get price history for a symbol and date range.
     * 
     * GET /api/v1/financial-data/history?symbol=AAPL&startDate=2023-01-01&endDate=2024-01-01
     * 
     * @param symbol Stock symbol
     * @param startDate Start date in format YYYY-MM-DD
     * @param endDate End date in format YYYY-MM-DD
     * @return List of price history records
     */
    @GetMapping("/history")
    public ResponseEntity<?> getPriceHistory(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<PriceHistory> history = yahooFinanceService.getPriceHistory(symbol, startDate, endDate);
        List<PriceHistoryDTO> dtos = history.stream()
                .map(PriceHistoryDTO::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "records", dtos
        ));
    }
    
    /**
     * Calculate moving average.
     * 
     * GET /api/v1/financial-data/moving-average?symbol=AAPL&days=50
     * 
     * @param symbol Stock symbol
     * @param days Number of days for moving average (default: 50)
     * @return Moving average value
     */
    @GetMapping("/moving-average")
    public ResponseEntity<?> getMovingAverage(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "50") int days) {
        
        return yahooFinanceService.calculateMovingAverage(symbol, days)
                .map(ma -> ResponseEntity.ok(Map.of(
                        "symbol", symbol,
                        "days", days,
                        "movingAverage", ma
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Calculate volatility.
     * 
     * GET /api/v1/financial-data/volatility?symbol=AAPL&days=30
     * 
     * @param symbol Stock symbol
     * @param days Number of days for volatility calculation (default: 30)
     * @return Volatility value
     */
    @GetMapping("/volatility")
    public ResponseEntity<?> getVolatility(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "30") int days) {
        
        return yahooFinanceService.calculateVolatility(symbol, days)
                .map(vol -> ResponseEntity.ok(Map.of(
                        "symbol", symbol,
                        "days", days,
                        "volatility", vol
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
