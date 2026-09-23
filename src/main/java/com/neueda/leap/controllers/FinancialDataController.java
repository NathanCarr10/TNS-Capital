package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PriceHistoryDTO;
import com.neueda.leap.model.PriceHistory;
import com.neueda.leap.services.YahooFinanceService;
import com.neueda.leap.services.PythonYFinanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/financial-data")
public class FinancialDataController {
    
    private final YahooFinanceService yahooFinanceService;
    private final PythonYFinanceService pythonYFinanceService;
    
    public FinancialDataController(YahooFinanceService yahooFinanceService, 
                                   PythonYFinanceService pythonYFinanceService) {
        this.yahooFinanceService = yahooFinanceService;
        this.pythonYFinanceService = pythonYFinanceService;
    }
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
    
    @GetMapping("/fetch-multiple")
    public ResponseEntity<?> fetchMultipleWithDelay(
            @RequestParam String symbols,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "5") int delaySeconds) {
        
        String[] symbolArray = symbols.split(",");
        Map<String, Object> results = new java.util.LinkedHashMap<>();
        
        for (int i = 0; i < symbolArray.length; i++) {
            String symbol = symbolArray[i].trim();
            
            try {
                List<PriceHistory> data = yahooFinanceService.fetchAndStoreHistoricalData(symbol, startDate, endDate);
                results.put(symbol, Map.of(
                        "status", "success",
                        "recordsStored", data.size()
                ));
            } catch (Exception e) {
                results.put(symbol, Map.of(
                        "status", "error",
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
            }
            
            // Add delay between fetches (except after last symbol)
            if (i < symbolArray.length - 1) {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/fetch-python")
    public ResponseEntity<?> fetchViaPhonYFinance(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            List<PriceHistory> data = pythonYFinanceService.fetchHistoricalData(symbol, startDate, endDate);
            
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol,
                    "recordsStored", data.size(),
                    "startDate", startDate,
                    "endDate", endDate,
                    "source", "python-yfinance"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "symbol", symbol,
                    "error", "Failed to fetch financial data for symbol: " + symbol,
                    "message", e.getMessage(),
                    "source", "python-yfinance"
            ));
        }
    }
}

