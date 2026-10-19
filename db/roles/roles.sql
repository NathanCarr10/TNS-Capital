-- Least-privilege login roles, one per service (spec §5, OWASP A01/A05).
--
-- The database owner ($DB_USER, POSTGRES_USER) is a superuser: it creates the
-- schema and is for administrators only. Each service logs in with a role
-- that can do what that service needs and nothing more, so a flaw in one
-- service cannot drop tables, read password hashes or run commands on the
-- database server.
--
-- Run once by docker-entrypoint-initdb.sh, after the tables and views, with the
-- passwords as psql variables (never spliced into the SQL text).

CREATE ROLE tns_api    LOGIN PASSWORD :'api_password';
CREATE ROLE tns_auth   LOGIN PASSWORD :'auth_password';
CREATE ROLE tns_etl    LOGIN PASSWORD :'etl_password';
CREATE ROLE tns_reader LOGIN PASSWORD :'reader_password';
CREATE ROLE tns_strategy LOGIN PASSWORD :'strategy_password';

-- Only these roles (and the owner) may connect, and none of them may create
-- objects in the public schema or temporary tables
REVOKE ALL ON DATABASE tns_capital FROM PUBLIC;
GRANT CONNECT ON DATABASE tns_capital TO tns_api, tns_auth, tns_etl, tns_reader, tns_strategy;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO tns_api, tns_auth, tns_etl, tns_reader, tns_strategy;

-- Trading API: reads and writes the trading tables; reads only the login name
-- and account link of a user, never the password hash
GRANT SELECT, INSERT, UPDATE, DELETE
    ON accounts, instruments, orders, positions, executions, order_history, dlq_messages, trade_event_log
    TO tns_api;
GRANT SELECT (username, account_id) ON users TO tns_api;
-- ...and whether an account has an ACTIVE strategy, before taking an order from
-- the strategy service (AccountAccessGuard)
GRANT SELECT (account_id, status) ON strategies TO tns_api;
-- Newsletter issues are published once and never changed
GRANT SELECT, INSERT ON newsletter_issues TO tns_api;

-- Auth service: users and their refresh tokens, and opening the account of a
-- user who registers (RETURNING id, account_number)
GRANT SELECT, INSERT, UPDATE ON users TO tns_auth;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO tns_auth;
GRANT INSERT ON accounts TO tns_auth;
GRANT SELECT (id, account_number) ON accounts TO tns_auth;

GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO tns_api, tns_auth, tns_strategy;

-- ETL: reads the trading tables it extracts and owns the analytics schema it
-- loads (etl/schema.sql creates its tables there). CREATE on the database lets
-- etl/schema.sql run its "CREATE SCHEMA IF NOT EXISTS analytics"
GRANT SELECT ON accounts, instruments, orders TO tns_etl;
GRANT CREATE ON DATABASE tns_capital TO tns_etl;
CREATE SCHEMA analytics AUTHORIZATION tns_etl;

-- Dashboard: reads the live tables, the reporting views and the analytics
-- schema; reads users only to show who traded
GRANT SELECT
    ON accounts, instruments, orders, positions, executions, order_history, dlq_messages, trade_event_log,
       strategies, strategy_signals, v_positions_detail, v_order_history, v_account_summary
    TO tns_reader;
GRANT SELECT (id, username, account_id) ON users TO tns_reader;
GRANT USAGE ON SCHEMA analytics TO tns_reader;
ALTER DEFAULT PRIVILEGES FOR ROLE tns_etl IN SCHEMA analytics GRANT SELECT ON TABLES TO tns_reader;

-- Strategy service: its own tables; who owns which account (never the password
-- hash); tradable instruments; the outcome of the orders it placed; and the
-- daily price history the ETL loads into analytics, for backtests
GRANT SELECT, INSERT, UPDATE ON strategies, strategy_signals TO tns_strategy;
GRANT SELECT (username, account_id) ON users TO tns_strategy;
GRANT SELECT (symbol, tradable) ON instruments TO tns_strategy;
GRANT SELECT (id, account_id, symbol, side, quantity, price, status, created_on) ON orders TO tns_strategy;
GRANT USAGE ON SCHEMA analytics TO tns_strategy;
ALTER DEFAULT PRIVILEGES FOR ROLE tns_etl IN SCHEMA analytics GRANT SELECT ON TABLES TO tns_strategy;
