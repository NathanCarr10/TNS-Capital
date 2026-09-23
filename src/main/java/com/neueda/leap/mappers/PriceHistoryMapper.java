package com.neueda.leap.mappers;

import com.neueda.leap.model.PriceHistory;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for PriceHistory persistence.
 * 
 * Handles all database operations for price history records.
 */
@Mapper
public interface PriceHistoryMapper {
    
    @Insert("INSERT INTO price_history (symbol, date, open_price, high_price, low_price, " +
            "close_price, adj_close_price, volume) VALUES (#{symbol}, #{date}, #{openPrice}, " +
            "#{highPrice}, #{lowPrice}, #{closePrice}, #{adjClosePrice}, #{volume})")
    void insert(PriceHistory priceHistory);
    
    @Insert("<script>" +
            "INSERT INTO price_history (symbol, date, open_price, high_price, low_price, " +
            "close_price, adj_close_price, volume) VALUES " +
            "<foreach item='item' collection='list' separator=','>" +
            "(#{item.symbol}, #{item.date}, #{item.openPrice}, #{item.highPrice}, " +
            "#{item.lowPrice}, #{item.closePrice}, #{item.adjClosePrice}, #{item.volume})" +
            "</foreach>" +
            "</script>")
    void insertAll(List<PriceHistory> priceHistories);
    
    @Select("SELECT * FROM price_history WHERE symbol = #{symbol} AND date = #{date}")
    Optional<PriceHistory> findBySymbolAndDate(String symbol, LocalDate date);
    
    @Select("SELECT * FROM price_history WHERE symbol = #{symbol} ORDER BY date DESC")
    List<PriceHistory> findBySymbol(String symbol);
    
    @Select("SELECT * FROM price_history WHERE symbol = #{symbol} " +
            "AND date BETWEEN #{startDate} AND #{endDate} ORDER BY date DESC")
    List<PriceHistory> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate);
    
    @Delete("DELETE FROM price_history WHERE symbol = #{symbol}")
    void deleteBySymbol(String symbol);
    
    @Select("SELECT COUNT(*) FROM price_history WHERE symbol = #{symbol}")
    long countBySymbol(String symbol);
}
