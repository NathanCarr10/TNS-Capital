-- Orders seed data
-- Orders reference accounts via their business account_id, not the surrogate PK
INSERT INTO orders (account_id, symbol, side, quantity, price, status, idempotency_key, status_reason) VALUES
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'ACME',  'BUY',  100, 25.00,  'FILLED',      'seed-key-1',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'GOOG',  'BUY',  50, 10.00,   'FILLED',      'seed-key-2',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'BOND1', 'BUY',  100,  40.00, 'FILLED',      'seed-key-3',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'BOND1', 'SELL', 50,  40.00,  'FILLED',      'seed-key-4',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1003'), 'GOOG',  'BUY',  10,  12.00,  'REJECTED',    'seed-key-5',    'Account ACC-1003 is suspended'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'GLOB',  'BUY',  10,  41.00,  'REJECTED',    'seed-key-6',    'Instrument GLOB is not tradable'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'TECH',  'BUY',  75,  50.50,  'FILLED',      'seed-key-7',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1004'), 'GOOG',  'BUY',  30,  120.00, 'FILLED',      'seed-key-8',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1005'), 'APPL',  'BUY',  50,  155.00, 'FILLED',      'seed-key-9',    NULL),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1002'), 'MSFT',  'SELL', 20,  310.00, 'REJECTED',    'seed-key-10',   'Cannot sell 20 of MSFT, only 0 available'),
    ((SELECT id FROM accounts WHERE account_number = 'ACC-1001'), 'GOLD',  'BUY',  100, 65.25,  'CANCELLED',   'seed-key-11',   'Cancelled by trader')
ON CONFLICT (idempotency_key) DO NOTHING;