package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.PriceHistory;
import com.neueda.leap.repositories.PriceHistoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PriceHistoryRepository using JdbcTemplate.
 * 
 * Uses JDBC for direct database operations.
 */
@Component
public class PriceHistoryRepositoryImpl implements PriceHistoryRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final RowMapper<PriceHistory> rowMapper = new RowMapper<PriceHistory>() {
        @Override
        public PriceHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            PriceHistory ph = new PriceHistory();
            ph.setId(rs.getLong("id"));
            ph.setSymbol(rs.getString("symbol"));
            ph.setDate(rs.getObject("date", LocalDate.class));
            ph.setOpenPrice(rs.getBigDecimal("open_price"));
            ph.setHighPrice(rs.getBigDecimal("high_price"));
            ph.setLowPrice(rs.getBigDecimal("low_price"));
            ph.setClosePrice(rs.getBigDecimal("close_price"));
            ph.setAdjClosePrice(rs.getBigDecimal("adj_close_price"));
            ph.setVolume(rs.getLong("volume"));
            return ph;
        }
    };
    
    public PriceHistoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void save(PriceHistory priceHistory) {
        String sql = "INSERT INTO price_history (symbol, date, open_price, high_price, low_price, " +
                     "close_price, adj_close_price, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
                priceHistory.getSymbol(),
                priceHistory.getDate(),
                priceHistory.getOpenPrice(),
                priceHistory.getHighPrice(),
                priceHistory.getLowPrice(),
                priceHistory.getClosePrice(),
                priceHistory.getAdjClosePrice(),
                priceHistory.getVolume()
        );
    }
    
    @Override
    public void saveAll(List<PriceHistory> priceHistories) {
        if (!priceHistories.isEmpty()) {
            String sql = "INSERT INTO price_history (symbol, date, open_price, high_price, low_price, " +
                         "close_price, adj_close_price, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            jdbcTemplate.batchUpdate(sql, priceHistories, priceHistories.size(),
                    (ps, ph) -> {
                        ps.setString(1, ph.getSymbol());
                        ps.setObject(2, ph.getDate());
                        ps.setBigDecimal(3, ph.getOpenPrice());
                        ps.setBigDecimal(4, ph.getHighPrice());
                        ps.setBigDecimal(5, ph.getLowPrice());
                        ps.setBigDecimal(6, ph.getClosePrice());
                        ps.setBigDecimal(7, ph.getAdjClosePrice());
                        ps.setLong(8, ph.getVolume());
                    }
            );
        }
    }
    
    @Override
    public Optional<PriceHistory> findBySymbolAndDate(String symbol, LocalDate date) {
        String sql = "SELECT * FROM price_history WHERE symbol = ? AND date = ?";
        List<PriceHistory> results = jdbcTemplate.query(sql, rowMapper, symbol, date);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    @Override
    public List<PriceHistory> findBySymbol(String symbol) {
        String sql = "SELECT * FROM price_history WHERE symbol = ? ORDER BY date DESC";
        return jdbcTemplate.query(sql, rowMapper, symbol);
    }
    
    @Override
    public List<PriceHistory> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM price_history WHERE symbol = ? " +
                     "AND date BETWEEN ? AND ? ORDER BY date DESC";
        return jdbcTemplate.query(sql, rowMapper, symbol, startDate, endDate);
    }
    
    @Override
    public void deleteBySymbol(String symbol) {
        String sql = "DELETE FROM price_history WHERE symbol = ?";
        jdbcTemplate.update(sql, symbol);
    }
    
    @Override
    public long countBySymbol(String symbol) {
        String sql = "SELECT COUNT(*) FROM price_history WHERE symbol = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, symbol);
        return count != null ? count : 0;
    }
}
