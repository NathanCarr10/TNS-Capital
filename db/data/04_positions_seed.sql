-- Positions seed data
-- Positions reflect only the FILLED orders above
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'INTC',  100, 21.50),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'GOOGL', 50,  165.00),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'TLT',   50,  90.00),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'PFE',   75,  25.50),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1004'), 'GOOGL', 30,  170.00),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1005'), 'AAPL',  50,  225.00)
ON CONFLICT (account_id, symbol) DO NOTHING;
