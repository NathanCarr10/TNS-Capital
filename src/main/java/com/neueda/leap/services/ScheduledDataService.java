package com.neueda.leap.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;

@Service
public class ScheduledDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledDataService.class);
    
    @Autowired
    private YahooFinanceService yahooFinanceService;
    
    // Runs every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void refreshDailyPriceData() {
        logger.info("Starting scheduled price data refresh...");
        
        // Add stocks you want to track
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "TESLA"};
        
        for (String symbol : symbols) {
            try {
                // Fetch last 30 days of data
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusDays(30);
                
                yahooFinanceService.fetchAndStoreHistoricalData(symbol, startDate, endDate);
                logger.info("Successfully refreshed data for {}", symbol);
            } catch (Exception e) {
                logger.error("Failed to refresh data for {}: {}", symbol, e.getMessage());
            }
        }
        
        logger.info("Completed scheduled price data refresh");
    }
}
