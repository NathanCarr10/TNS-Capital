-- Accounts seed data
INSERT INTO accounts (account_number, holder_name, email, cash_balance, status) VALUES
    ('ACC-1001', 'John Doe',       'john.doe@example.com',       5000.00, 'ACTIVE'),
    ('ACC-1002', 'Jane Smith',     'jane.smith@example.com',     12000.00, 'ACTIVE'),
    ('ACC-1003', 'Bob Lee',        'bob.lee@example.com',        1000.00, 'SUSPENDED'),
    ('ACC-1004', 'Alice Johnson',  'alice.johnson@example.com',  8500.00, 'ACTIVE'),
    ('ACC-1005', 'Charlie Brown',  'charlie.brown@example.com',  15000.00, 'ACTIVE'),
    ('ACC-1006', 'Diana Prince',   'diana.prince@example.com',   2500.00, 'ACTIVE'),
    ('ACC-1007', 'Eve Wilson',     'eve.wilson@example.com',     20000.00, 'ACTIVE'),
    ('ACC-1008', 'Frank Miller',   'frank.miller@example.com',   500.00, 'SUSPENDED'),
    ('ACC-1009', 'Grace Hopper',   'grace.hopper@example.com',   11000.00, 'ACTIVE'),
    ('ACC-1010', 'Henry Foster',   'henry.foster@example.com',   7250.00, 'ACTIVE');
