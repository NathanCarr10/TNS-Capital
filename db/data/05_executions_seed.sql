-- One execution per FILLED seed order, at the order price, so seeded fills
-- look the same as fills reported by the execution engine. Filled 0.5 to 2
-- seconds after the order, like the broker simulator's delay.
INSERT INTO executions (id, order_id, quantity, price, venue, executed_on)
SELECT gen_random_uuid(), o.id, o.quantity, o.price, 'SEED',
       o.created_on + INTERVAL '500 milliseconds' + random() * INTERVAL '1500 milliseconds'
  FROM orders o
 WHERE o.status = 'FILLED'
  AND (o.idempotency_key LIKE 'seed-key-%' OR o.idempotency_key LIKE 'seed-demo-jane-%')
ON CONFLICT (order_id) DO NOTHING;
