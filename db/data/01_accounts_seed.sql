-- Accounts seed data
INSERT INTO accounts (account_number, holder_name, cash_balance, status, owner_username) VALUES
    ('ACC-1001', 'John Doe',       5000.00, 'ACTIVE', 'john'),
    ('ACC-1002', 'Jane Smith',     12000.00, 'ACTIVE', 'jane'),
    ('ACC-1003', 'Bob Lee',        1000.00, 'SUSPENDED', 'bob'),
    ('ACC-1004', 'Alice Johnson',  8500.00, 'ACTIVE', 'alice'),
    ('ACC-1005', 'Charlie Brown',  15000.00, 'ACTIVE', 'charlie'),
    ('ACC-1006', 'Diana Prince',   2500.00, 'ACTIVE', 'diana'),
    ('ACC-1007', 'Eve Wilson',     20000.00, 'ACTIVE', 'eve'),
    ('ACC-1008', 'Frank Miller',   500.00, 'SUSPENDED', 'frank'),
    ('ACC-1009', 'Grace Hopper',   11000.00, 'ACTIVE', 'grace'),
    ('ACC-1010', 'Henry Foster',   7250.00, 'ACTIVE', 'henry');
