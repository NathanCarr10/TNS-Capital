-- Orders seed data
-- Orders reference accounts via their business account_id, not the surrogate PK
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
    ('11111111-1111-1111-1111-111111111111',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME',  'BUY',  100, 25.00, 'FILLED',   'seed-key-1', '2026-08-01 09:00:00'),
    ('22222222-2222-2222-2222-222222222222',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB',  'BUY',  200, 10.00, 'FILLED',   'seed-key-2', '2026-08-02 09:30:00'),
    ('33333333-3333-3333-3333-333333333333',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'BUY',  50,  40.00, 'FILLED',   'seed-key-3', '2026-08-02 10:00:00'),
    ('44444444-4444-4444-4444-444444444444',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'GLOB',  'BUY',  10,  12.00, 'REJECTED', 'seed-key-4', '2026-08-03 11:00:00'),
    ('55555555-5555-5555-5555-555555555555',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'SELL', 10,  41.00, 'NEW',      'seed-key-5', '2026-08-04 13:15:00');
