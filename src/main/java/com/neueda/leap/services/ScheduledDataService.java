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
    private static final long SYMBOL_DELAY_MS = 5000;
    
    @Autowired
    private PythonYFinanceService pythonYFinanceService;
    
    @Scheduled(cron = "0 0 9 * * *")
    public void refreshDailyPriceData() {
        logger.info("Starting scheduled price data refresh via Python yfinance...");
        
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "TSLA"};
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        for (int i = 0; i < symbols.length; i++) {
            String symbol = symbols[i];
            
            try {
                logger.info("Fetching last 30 days of {} from {} to {}", symbol, startDate, endDate);
                pythonYFinanceService.fetchHistoricalData(symbol, startDate, endDate, 3);
                logger.info("Successfully refreshed {} ", symbol);
                
            } catch (Exception e) {
                logger.error("Failed to refresh {}: {}", symbol, e.getMessage());
            }
            
            if (i < symbols.length - 1) {
                try {
                    Thread.sleep(SYMBOL_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Scheduler interrupted during delay");
                }
            }
        }
        
        logger.info("Daily price data refresh completed");
    }
}
