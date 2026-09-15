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
        (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'SELL', 10,  41.00, 'NEW',      'seed-key-5', '2026-08-04 13:15:00'),
    ('66666666-6666-6666-6666-666666666666',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1003'), 'TECH',  'BUY',  75,  50.50, 'FILLED',   'seed-key-6', '2026-08-05 10:15:00'),
    ('77777777-7777-7777-7777-777777777777',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1004'), 'GOOG',  'BUY',  30,  120.00, 'FILLED',  'seed-key-7', '2026-08-06 11:30:00'),
    ('88888888-8888-8888-8888-888888888888',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1005'), 'APPL',  'BUY',  50,  155.00, 'FILLED',  'seed-key-8', '2026-08-07 14:00:00'),
    ('99999999-9999-9999-9999-999999999999',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1006'), 'MSFT',  'SELL', 20,  310.00, 'FILLED',  'seed-key-9', '2026-08-08 09:45:00'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        (SELECT id FROM accounts WHERE account_id = 'ACC-1007'), 'GOLD',  'BUY',  100, 65.25, 'FILLED',  'seed-key-10', '2026-08-09 13:20:00');
