-- Insert seed orders data
-- Uses gen_random_uuid() for unique order IDs and subqueries to lookup account IDs
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'ACME', 'BUY', 100, 25.00, 'FILLED', 'seed-key-1', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'GLOB', 'BUY', 200, 10.00, 'FILLED', 'seed-key-2', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'BUY', 50, 40.00, 'FILLED', 'seed-key-3', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1001'), 'GLOB', 'BUY', 10, 12.00, 'REJECTED', 'seed-key-4', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1002'), 'BOND1', 'SELL', 10, 41.00, 'NEW', 'seed-key-5', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1003'), 'TECH', 'BUY', 75, 50.50, 'FILLED', 'seed-key-6', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1004'), 'GOOG', 'BUY', 30, 120.00, 'FILLED', 'seed-key-7', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1005'), 'APPL', 'BUY', 50, 155.00, 'FILLED', 'seed-key-8', now()),
    (gen_random_uuid(), (SELECT id FROM accounts WHERE account_id = 'ACC-1006'), 'MSFT', 'SELL', 20, 310.00, 'FILLED', 'seed-key-9', now());
