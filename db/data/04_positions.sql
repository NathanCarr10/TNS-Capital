-- Positions seed data
-- Positions reflect only the FILLED orders above
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME',  100, 25.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB',  200, 10.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 50,  40.00);
