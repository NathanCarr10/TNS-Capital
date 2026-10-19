package com.neueda.leap.strategy.prices;

import com.neueda.leap.strategy.backtest.DailyClose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The daily price history the ETL loads into analytics.fact_daily_prices
 * (five years from Alpaca), for backtests.
 */
@Repository
public class DailyPriceRepository {
    private final JdbcTemplate jdbc;

    public DailyPriceRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /**
     * @return the symbol's closes between the two days (inclusive), oldest first
     */
    public List<DailyClose> closes(String symbol, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT trade_date, close FROM analytics.fact_daily_prices
                WHERE symbol = ? AND trade_date BETWEEN ? AND ? ORDER BY trade_date""",
                (row, n) -> new DailyClose(row.getObject("trade_date", LocalDate.class), row.getBigDecimal("close")),
                symbol, from, to);
    }
}
