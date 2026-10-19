package com.neueda.leap.strategy.accounts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookups in the trading platform's tables, which the strategy
 * service's database role may read but not change (db/roles/roles.sql):
 * who owns which account, which instruments are tradable, and what became of
 * the orders strategies placed.
 */
@Component
public class PlatformLookup {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PlatformLookup(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * @return the account linked to the user (opened when they registered); empty for admins
     */
    public Optional<Long> accountIdOf(String username) {
        return jdbc.query("SELECT account_id FROM users WHERE username = ? AND account_id IS NOT NULL",
                (row, n) -> row.getLong(1), username).stream().findFirst();
    }

    public boolean isTradable(String symbol) {
        return Boolean.TRUE.equals(jdbc.query("SELECT tradable FROM instruments WHERE symbol = ?",
                (row, n) -> row.getBoolean(1), symbol).stream().findFirst().orElse(false));
    }

    /**
     * @return the status (NEW, FILLED, REJECTED, CANCELLED) of each order found
     */
    public Map<UUID, String> orderStatuses(Collection<UUID> orderIds) {
        Map<UUID, String> statuses = new HashMap<>();
        if (orderIds.isEmpty()) {
            return statuses;
        }
        named.query("SELECT id, status FROM orders WHERE id IN (:ids)", new MapSqlParameterSource("ids", orderIds),
                row -> {
                    statuses.put(row.getObject("id", UUID.class), row.getString("status"));
                });
        return statuses;
    }

    /**
     * @return the account's FILLED orders, oldest first
     */
    public List<FilledOrder> filledOrders(Long accountId) {
        return jdbc.query("""
                SELECT id, symbol, side, quantity, price, created_on FROM orders
                WHERE account_id = ? AND status = 'FILLED' ORDER BY created_on, id""",
                (row, n) -> new FilledOrder(row.getObject("id", UUID.class), row.getString("symbol"),
                        row.getString("side"), row.getInt("quantity"), row.getBigDecimal("price"),
                        row.getTimestamp("created_on").toInstant()),
                accountId);
    }

    /**
     * An order the broker filled. The orders table keeps the order's price,
     * which for a filled order is its fill price.
     */
    public record FilledOrder(UUID id, String symbol, String side, int quantity, BigDecimal price, Instant placedAt) {
    }
}
