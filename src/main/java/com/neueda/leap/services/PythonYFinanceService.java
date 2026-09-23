package com.neueda.leap.services;

import com.neueda.leap.model.PriceHistory;
import com.neueda.leap.repositories.PriceHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper service for Python-based yfinance library.
 * Provides better rate-limit handling than Java yahoofinance-api.
 */
@Service
public class PythonYFinanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonYFinanceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceHistoryRepository priceHistoryRepository;
    private static final String PYTHON_SCRIPT_PATH = "scripts/fetch_yfinance.py";
    
    public PythonYFinanceService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }
    
    /**
     * Fetch historical data using Python yfinance library.
     * Better rate-limit handling and more reliable than Java library.
     * 
     * @param symbol Stock ticker
     * @param startDate Start date
     * @param endDate End date
     * @param maxRetries Maximum retry attempts
     * @return List of PriceHistory objects
     * @throws RuntimeException if fetch fails after retries
     */
    public List<PriceHistory> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate, int maxRetries) {
        try {
            logger.info("Fetching {} via Python yfinance (retries: {})", symbol, maxRetries);
            
            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                PYTHON_SCRIPT_PATH,
                symbol,
                startDate.toString(),
                endDate.toString(),
                String.valueOf(maxRetries)
            );
            
            // Don't redirect stderr to stdout
            Process process = pb.start();
            
            // Capture stdout (JSON output)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Log stderr (debug messages)
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    logger.debug("[Python] {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            String result = output.toString().trim();
            
            if (exitCode != 0 || result.isEmpty()) {
                logger.error("Python script failed with exit code {}: {}", exitCode, result);
                throw new RuntimeException("Python fetch failed: " + result);
            }
            
            // Parse JSON response
            JsonNode root = objectMapper.readTree(result);
            
            // Check for error response
            if (root.isObject() && root.has("error")) {
                String error = root.get("error").asText();
                logger.error("✗ Python fetch error for {}: {}", symbol, error);
                throw new RuntimeException("Fetch failed: " + error);
            }
            
            // Parse array response
            if (!root.isArray()) {
                logger.error("Unexpected response format: {}", result);
                throw new RuntimeException("Invalid response format");
            }
            
            List<PriceHistory> priceHistories = new ArrayList<>();
            root.forEach(node -> {
                try {
                    PriceHistory ph = new PriceHistory(
                        node.get("symbol").asText(),
                        LocalDate.parse(node.get("date").asText()),
                        new BigDecimal(node.get("open_price").asDouble()),
                        new BigDecimal(node.get("high_price").asDouble()),
                        new BigDecimal(node.get("low_price").asDouble()),
                        new BigDecimal(node.get("close_price").asDouble()),
                        new BigDecimal(node.get("adj_close_price").asDouble()),
                        node.get("volume").asLong()
                    );
                    priceHistories.add(ph);
                } catch (Exception e) {
                    logger.warn("Failed to parse price history record: {}", e.getMessage());
                }
            });
            
            // Save to database
            priceHistoryRepository.saveAll(priceHistories);
            logger.info("✓ Successfully fetched and stored {} records for {} via Python", priceHistories.size(), symbol);
            return priceHistories;
            
        } catch (IOException e) {
            logger.error("IO error during Python fetch: {}", e.getMessage());
            throw new RuntimeException("IO error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Python fetch interrupted: {}", e.getMessage());
            throw new RuntimeException("Fetch interrupted", e);
        }
    }
    
    /**
     * Fetch with default retry count.
     */
    public List<PriceHistory> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        return fetchHistoricalData(symbol, startDate, endDate, 3);
    }
}
