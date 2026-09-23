package com.neueda.leap.repositories;

import com.neueda.leap.model.PriceHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for PriceHistory persistence.
 * 
 * Handles storage and retrieval of historical price data.
 * Decouples business logic from storage implementation.
 */
public interface PriceHistoryRepository {
    /**
     * Save a single price history record.
     *
     * @param priceHistory the price history to save
     */
    void save(PriceHistory priceHistory);

    /**
     * Save multiple price history records in batch.
     *
     * @param priceHistories list of price history records
     */
    void saveAll(List<PriceHistory> priceHistories);

    /**
     * Find a price history record by symbol and date.
     *
     * @param symbol the instrument symbol
     * @param date the date
     * @return Optional containing the price history if found
     */
    Optional<PriceHistory> findBySymbolAndDate(String symbol, LocalDate date);

    /**
     * Find all price history records for a symbol.
     *
     * @param symbol the instrument symbol
     * @return List of price history records, ordered by date descending
     */
    List<PriceHistory> findBySymbol(String symbol);

    /**
     * Find price history records for a symbol within a date range.
     *
     * @param symbol the instrument symbol
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return List of price history records, ordered by date descending
     */
    List<PriceHistory> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * Delete all price history records for a symbol.
     *
     * @param symbol the instrument symbol
     */
    void deleteBySymbol(String symbol);

    /**
     * Count price history records for a symbol.
     *
     * @param symbol the instrument symbol
     * @return number of records
     */
    long countBySymbol(String symbol);
}
