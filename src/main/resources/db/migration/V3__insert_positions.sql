-- Insert seed positions data
-- Uses subqueries to lookup account and instrument IDs
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME', 100, 25.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB', 200, 10.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 50, 40.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1003'), 'TECH', 75, 50.50),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1004'), 'GOOG', 30, 120.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1005'), 'APPL', 50, 155.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1007'), 'GOLD', 100, 65.25),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'GLOB', 50, 12.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1004'), 'BOND1', 100, 40.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1009'), 'UTIL', 60, 30.00);
