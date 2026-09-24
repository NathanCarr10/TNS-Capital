-- TNS Capital Database Initialization Script
-- Table Creation Phase
-- Accounts table
DROP TABLE IF EXISTS accounts CASCADE;

CREATE TABLE accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    VARCHAR(32)  NOT NULL UNIQUE,
    holder_name   VARCHAR(255) NOT NULL,
    cash_balance  NUMERIC(18,2) NOT NULL CHECK (cash_balance >= 0),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    version       INT NOT NULL DEFAULT 0,
    last_updated  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed Data Phase
INSERT INTO accounts (account_id, holder_name, cash_balance, status) VALUES
    ('ACC-1001', 'John Doe',       5000.00, 'ACTIVE'),
    ('ACC-1002', 'Jane Smith',     12000.00, 'ACTIVE'),
    ('ACC-1003', 'Bob Lee',        1000.00, 'SUSPENDED'),
    ('ACC-1004', 'Alice Johnson',  8500.00, 'ACTIVE'),
    ('ACC-1005', 'Charlie Brown',  15000.00, 'ACTIVE'),
    ('ACC-1006', 'Diana Prince',   2500.00, 'ACTIVE'),
    ('ACC-1007', 'Eve Wilson',     20000.00, 'ACTIVE'),
    ('ACC-1008', 'Frank Miller',   500.00, 'SUSPENDED'),
    ('ACC-1009', 'Grace Hopper',   11000.00, 'ACTIVE'),
    ('ACC-1010', 'Henry Foster',   7250.00, 'ACTIVE');