package com.neueda.leap.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.io.IOException;

@Service
public class ScheduledDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledDataService.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 5000; // 5 seconds between symbols
    private static final long RETRY_DELAY_MS = 30000; // 30 seconds for retries
    
    @Autowired
    private YahooFinanceService yahooFinanceService;
    
    // Runs every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void refreshDailyPriceData() {
        logger.info("Starting scheduled price data refresh with rate-limit protection...");
        
        // Add stocks you want to track
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "TESLA"};
        
        for (int i = 0; i < symbols.length; i++) {
            String symbol = symbols[i];
            boolean success = false;
            int attempt = 0;
            
            while (!success && attempt < MAX_RETRIES) {
                try {
                    logger.info("Fetching data for {} (attempt {}/{})", symbol, attempt + 1, MAX_RETRIES);
                    
                    // Fetch last 30 days of data
                    LocalDate endDate = LocalDate.now();
                    LocalDate startDate = endDate.minusDays(30);
                    
                    yahooFinanceService.fetchAndStoreHistoricalData(symbol, startDate, endDate);
                    logger.info("✓ Successfully refreshed data for {}", symbol);
                    success = true;
                    
                } catch (Exception e) {
                    attempt++;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    
                    if (errorMsg.contains("429") || errorMsg.contains("rate")) {
                        logger.warn("Rate-limited on {}. Attempt {}/{}. Waiting before retry...", symbol, attempt, MAX_RETRIES);
                        if (attempt < MAX_RETRIES) {
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    } else {
                        logger.error("Failed to refresh {} (attempt {}/{}): {}", symbol, attempt, MAX_RETRIES, errorMsg);
                        if (attempt < MAX_RETRIES) {
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            }
            
            if (!success) {
                logger.error("✗ Failed to refresh {} after {} attempts", symbol, MAX_RETRIES);
            }
            
            // Add delay between symbol fetches to avoid rate-limiting
            if (i < symbols.length - 1) {
                try {
                    logger.info("Waiting {} seconds before next symbol fetch...", INITIAL_DELAY_MS / 1000);
                    Thread.sleep(INITIAL_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        logger.info("Completed scheduled price data refresh");
    }
}
