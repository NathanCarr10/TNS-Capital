package com.neueda.leap.services;

import com.neueda.leap.model.PriceHistory;
import com.neueda.leap.repositories.PriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for fetching and managing financial data from Yahoo Finance.
 * 
 * Handles all interactions with Yahoo Finance API and price history storage.
 */
@Service
public class YahooFinanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceService.class);
    private final PriceHistoryRepository priceHistoryRepository;
    
    public YahooFinanceService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }
    
    /**
     * Fetch historical price data from Yahoo Finance and store in database.
     * Includes exponential backoff retry logic to handle rate limiting.
     * 
     * @param symbol Stock symbol (e.g., "AAPL")
     * @param startDate Starting date for historical data
     * @param endDate Ending date for historical data
     * @return List of PriceHistory objects
     * @throws RuntimeException if unable to fetch data after 5 attempts
     */
    public List<PriceHistory> fetchAndStoreHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        return fetchWithExponentialBackoff(symbol, startDate, endDate, 1, 5000);
    }
    
    /**
     * Fetch with exponential backoff retry strategy.
     * Max 5 attempts with delays: 5s → 10s → 20s → 40s → 80s
     * Adds jitter (±20%) to prevent thundering herd.
     * 
     * @param attempt Current attempt number (1-5)
     * @param delayMs Current delay in milliseconds
     * @return List of PriceHistory objects
     */
    private List<PriceHistory> fetchWithExponentialBackoff(String symbol, LocalDate startDate, LocalDate endDate, int attempt, long delayMs) {
        if (attempt > 5) {
            logger.error("✗ Max retries (5) exceeded for symbol: {}", symbol);
            throw new RuntimeException("Failed to fetch data for symbol: " + symbol + " after 5 attempts");
        }
        
        try {
            logger.info("Fetching {} from {} to {} (attempt {}/5)", symbol, startDate, endDate, attempt);
            
            Stock stock = YahooFinance.get(symbol);
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(java.sql.Date.valueOf(startDate));
            
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(java.sql.Date.valueOf(endDate));
            
            List<HistoricalQuote> history = stock.getHistory(startCal, endCal);
            
            List<PriceHistory> priceHistories = history.stream()
                    .map(quote -> new PriceHistory(
                            symbol,
                            quote.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                            new BigDecimal(quote.getOpen().toString()),
                            new BigDecimal(quote.getHigh().toString()),
                            new BigDecimal(quote.getLow().toString()),
                            new BigDecimal(quote.getClose().toString()),
                            new BigDecimal(quote.getAdjClose().toString()),
                            quote.getVolume()
                    ))
                    .collect(Collectors.toList());
            
            priceHistoryRepository.saveAll(priceHistories);
            logger.info("✓ Successfully stored {} records for {}", priceHistories.size(), symbol);
            
            return priceHistories;
            
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            boolean isRateLimited = errorMsg != null && (errorMsg.contains("429") || errorMsg.contains("Too Many Requests"));
            
            if (isRateLimited) {
                logger.warn("⚠️  Rate-limited (HTTP 429) for {}. Attempt {}/5. Backing off...", symbol, attempt);
                
                // Exponential backoff with jitter: 5s, 10s, 20s, 40s, 80s
                // Jitter: ±20% to avoid thundering herd
                long jitter = (long) (delayMs * 0.2 * (Math.random() * 2 - 1));
                long waitTime = delayMs + jitter;
                
                logger.info("  Waiting {}ms before retry...", waitTime);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                
                // Retry with doubled delay
                return fetchWithExponentialBackoff(symbol, startDate, endDate, attempt + 1, delayMs * 2);
            } else {
                logger.error("✗ Error fetching {} (not rate-limited): {}", symbol, errorMsg);
                throw new RuntimeException("Failed to fetch financial data for symbol: " + symbol, e);
            }
        }
    }
    
    /**
     * Get current price quote for a symbol.
     * 
     * @param symbol Stock symbol
     * @return Current price or empty if not found
     */
    public Optional<BigDecimal> getCurrentPrice(String symbol) {
        try {
            logger.info("Fetching current price for {}", symbol);
            Stock stock = YahooFinance.get(symbol);
            BigDecimal price = new BigDecimal(stock.getQuote().getPrice().toString());
            return Optional.of(price);
        } catch (IOException e) {
            logger.error("Error fetching current price for symbol: {}", symbol, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get price data from database for a date range.
     * 
     * @param symbol Stock symbol
     * @param startDate Start date
     * @param endDate End date
     * @return List of price history
     */
    public List<PriceHistory> getPriceHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        return priceHistoryRepository.findBySymbolAndDateRange(symbol, startDate, endDate);
    }
    
    /**
     * Calculate simple moving average.
     * 
     * @param symbol Stock symbol
     * @param days Number of days for moving average
     * @return Moving average value
     */
    public Optional<BigDecimal> calculateMovingAverage(String symbol, int days) {
        List<PriceHistory> history = priceHistoryRepository.findBySymbol(symbol);
        
        if (history.size() < days) {
            logger.warn("Insufficient data for {} day moving average on {}", days, symbol);
            return Optional.empty();
        }
        
        BigDecimal sum = history.stream()
                .limit(days)
                .map(PriceHistory::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return Optional.of(sum.divide(new BigDecimal(days), 6, java.math.RoundingMode.HALF_UP));
    }
    
    /**
     * Calculate volatility (standard deviation).
     * 
     * @param symbol Stock symbol
     * @param days Number of days to calculate
     * @return Volatility value
     */
    public Optional<BigDecimal> calculateVolatility(String symbol, int days) {
        List<PriceHistory> history = priceHistoryRepository.findBySymbol(symbol);
        
        if (history.size() < days) {
            logger.warn("Insufficient data for volatility calculation on {}", symbol);
            return Optional.empty();
        }
        
        List<BigDecimal> prices = history.stream()
                .limit(days)
                .map(PriceHistory::getClosePrice)
                .collect(Collectors.toList());
        
        BigDecimal mean = prices.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(prices.size()), 6, java.math.RoundingMode.HALF_UP);
        
        BigDecimal variance = prices.stream()
                .map(price -> price.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(prices.size()), 6, java.math.RoundingMode.HALF_UP);
        
        double volatility = Math.sqrt(variance.doubleValue());
        return Optional.of(new BigDecimal(volatility));
    }
}
