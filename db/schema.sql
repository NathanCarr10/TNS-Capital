-- TNS Capital - Trade Database Schema (Postgres)
-- Component 1: normalised schema for accounts, instruments, orders, positions.

DROP TABLE IF EXISTS positions;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS instruments;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    VARCHAR(32)  NOT NULL UNIQUE,
    holder_name   VARCHAR(255) NOT NULL,
    cash_balance  NUMERIC(18,2) NOT NULL CHECK (cash_balance >= 0),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    version       INT NOT NULL DEFAULT 0,
    last_updated  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE instruments (
    symbol      VARCHAR(20) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    currency    VARCHAR(3)  NOT NULL,
    tradable    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES accounts(id),
    symbol           VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side             VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity         INT NOT NULL CHECK (quantity > 0),
    price            NUMERIC(18,2) NOT NULL CHECK (price > 0),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    created_on       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE positions (
    account_id    BIGINT NOT NULL REFERENCES accounts(id),
    symbol        VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity      INT NOT NULL CHECK (quantity >= 0),
    average_cost  NUMERIC(18,2) NOT NULL CHECK (average_cost >= 0),
    PRIMARY KEY (account_id, symbol)
);

-- Lookups used by the order-history and portfolio endpoints
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);

INSERT INTO accounts (account_id, holder_name, cash_balance, status) VALUES
    ('ACC-1001', 'John Doe',   5000.00, 'ACTIVE'),
    ('ACC-1002', 'Jane Smith', 12000.00, 'ACTIVE'),
    ('ACC-1003', 'Bob Lee',    1000.00, 'SUSPENDED');

INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('ACME',  'Acme Corp',           'Equity', 'USD', TRUE),
    ('GLOB',  'Global Growth Fund',  'Fund',   'USD', TRUE),
    ('BOND1', 'Corporate Bond Fund', 'Bond',   'USD', TRUE);

-- Orders reference accounts via their business account_id, not the surrogate PK
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
    ('11111111-1111-1111-1111-111111111111',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME',  'BUY',  100, 25.00, 'FILLED',   'seed-key-1', '2026-08-01 09:00:00'),
    ('22222222-2222-2222-2222-222222222222',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB',  'BUY',  200, 10.00, 'FILLED',   'seed-key-2', '2026-08-02 09:30:00'),
    ('33333333-3333-3333-3333-333333333333',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'BUY',  50,  40.00, 'FILLED',   'seed-key-3', '2026-08-02 10:00:00'),
    ('44444444-4444-4444-4444-444444444444',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'GLOB',  'BUY',  10,  12.00, 'REJECTED', 'seed-key-4', '2026-08-03 11:00:00'),
    ('55555555-5555-5555-5555-555555555555',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'SELL', 10,  41.00, 'NEW',      'seed-key-5', '2026-08-04 13:15:00');

-- Positions reflect only the FILLED orders above
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME',  100, 25.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB',  200, 10.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 50,  40.00);