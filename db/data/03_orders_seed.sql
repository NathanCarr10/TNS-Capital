-- Orders seed data
-- Orders reference accounts via their business account_id, not the surrogate PK
-- Prices are illustrative past fill prices, not live market prices
INSERT INTO orders (account_id, symbol, side, quantity, price, status, idempotency_key, status_reason) VALUES
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'INTC',  'BUY',  100, 21.50,  'FILLED',      'seed-key-1',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'GOOGL', 'BUY',  50,  165.00, 'FILLED',      'seed-key-2',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'TLT',   'BUY',  100, 90.00,  'FILLED',      'seed-key-3',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'TLT',   'SELL', 50,  91.20,  'FILLED',      'seed-key-4',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1003'), 'GOOGL', 'BUY',  10,  166.00, 'REJECTED',    'seed-key-5',    'Account ACC-1003 is suspended'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'ARKK',  'BUY',  10,  55.00,  'REJECTED',    'seed-key-6',    'Instrument ARKK is not tradable'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'PFE',   'BUY',  75,  25.50,  'FILLED',      'seed-key-7',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1004'), 'GOOGL', 'BUY',  30,  170.00, 'FILLED',      'seed-key-8',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1005'), 'AAPL',  'BUY',  50,  225.00, 'FILLED',      'seed-key-9',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'MSFT',  'SELL', 20,  420.00, 'REJECTED',    'seed-key-10',   'Cannot sell 20 of MSFT, only 0 available'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'GLD',   'BUY',  10,  230.00, 'CANCELLED',   'seed-key-11',   'Cancelled by trader')
ON CONFLICT (idempotency_key) DO NOTHING;
