-- Positions seed data
-- Positions reflect only the FILLED orders above
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME',  100, 25.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GOOG',  50,  10.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 50,  40.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'TECH',  75,  50.50),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1004'), 'GOOG',  30,  120.00),
    ((SELECT id FROM accounts WHERE account_id = 'ACC-1005'), 'APPL',  50,  155.00)
ON CONFLICT (account_id, symbol) DO NOTHING;
