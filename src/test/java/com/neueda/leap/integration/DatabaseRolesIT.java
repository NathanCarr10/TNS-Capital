package com.neueda.leap.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The services' least-privilege login roles (db/roles/roles.sql): each can
 * do what its service needs and is refused everything else. Runs against the
 * shared test database, created by the same init script as the real one; the
 * roles' passwords default to the owner's there.
 */
@DisplayName("Database roles")
class DatabaseRolesIT extends AbstractIntegrationTest {

    private static Connection connectAs(String role) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), role, postgres.getPassword());
    }

    /** Runs the statement in a transaction that is rolled back, so nothing is changed */
    private static void run(String role, String sql) throws SQLException {
        try (Connection connection = connectAs(role); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute(sql);
            } finally {
                connection.rollback();
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}")
    @DisplayName("Each service can do what it needs")
    @CsvSource(delimiter = '|', value = {
            "tns_api    | SELECT id, cash_balance, version FROM accounts",
            "tns_api    | UPDATE accounts SET holder_name = holder_name",
            "tns_api    | DELETE FROM positions",
            "tns_api    | SELECT username, account_id FROM users",
            "tns_api    | INSERT INTO trade_event_log SELECT * FROM trade_event_log WHERE false",
            "tns_api    | INSERT INTO newsletter_issues (week_start, gainers, losers, news) VALUES ('2026-10-12', '[]', '[]', '[]')",
            "tns_api    | SELECT id, week_start, gainers FROM newsletter_issues ORDER BY week_start DESC",
            "tns_auth   | INSERT INTO accounts (account_number, holder_name, cash_balance) VALUES ('T-1', 'T', 0) RETURNING id, account_number",
            "tns_auth   | INSERT INTO users (username, password_hash, created_at, updated_at) VALUES ('t', 'h', NOW(), NOW()) RETURNING id",
            "tns_auth   | DELETE FROM refresh_tokens",
            "tns_etl    | SELECT o.id, a.account_number, o.symbol FROM orders o JOIN accounts a ON a.id = o.account_id",
            "tns_etl    | CREATE TABLE IF NOT EXISTS analytics.role_check (i INT)",
            "tns_reader | SELECT u.username FROM orders o JOIN accounts a ON a.id = o.account_id LEFT JOIN users u ON u.account_id = a.id",
            "tns_reader | SELECT * FROM v_account_summary",
    })
    void allowed(String role, String sql) {
        assertDoesNotThrow(() -> run(role, sql));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @DisplayName("Each service is refused everything else")
    @CsvSource(delimiter = '|', value = {
            "tns_api    | SELECT password_hash FROM users",
            "tns_api    | SELECT * FROM refresh_tokens",
            "tns_api    | DROP TABLE orders",
            "tns_api    | CREATE TABLE extra (i INT)",
            "tns_api    | COPY (SELECT 1) TO PROGRAM 'id'",
            "tns_api    | TRUNCATE orders",
            "tns_api    | UPDATE newsletter_issues SET news = '[]'",
            "tns_api    | DELETE FROM newsletter_issues",
            "tns_auth   | SELECT cash_balance FROM accounts",
            "tns_auth   | UPDATE accounts SET cash_balance = 1000000",
            "tns_auth   | SELECT * FROM orders",
            "tns_etl    | SELECT * FROM users",
            "tns_etl    | UPDATE orders SET status = 'FILLED'",
            "tns_reader | SELECT password_hash FROM users",
            "tns_reader | UPDATE accounts SET cash_balance = 1000000",
            "tns_reader | CREATE TABLE analytics.extra (i INT)",
    })
    void refused(String role, String sql) {
        assertThrows(SQLException.class, () -> run(role, sql));
    }
}
