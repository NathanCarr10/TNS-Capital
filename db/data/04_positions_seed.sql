-- Positions seed data: the net of the FILLED orders in 03_orders_seed.sql, at the
-- average cost of the buys (sells do not change it)
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'AAPL', 15, 228.21),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'INTC', 146, 22.11),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'GLD', 7, 210.30),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'INTC', 45, 22.41),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'KO', 87, 73.71),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'SPY', 4, 555.74),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1004'), 'QQQ', 5, 477.10),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1005'), 'NVDA', 4, 122.58),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1005'), 'TSLA', 16, 242.28),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1006'), 'GLD', 15, 223.83),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1007'), 'JPM', 4, 207.96),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1007'), 'MSFT', 6, 405.94),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1007'), 'TLT', 37, 88.99),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1007'), 'TSLA', 11, 248.18),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1009'), 'TSLA', 8, 244.68),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1010'), 'AMZN', 15, 180.35),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1010'), 'KO', 15, 77.79)
ON CONFLICT (account_id, symbol) DO NOTHING;
