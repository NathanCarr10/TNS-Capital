-- One execution per FILLED seed order, at the order price, so seeded fills
-- look the same as fills reported by the execution engine.
INSERT INTO executions (id, order_id, quantity, price, venue, executed_on)
SELECT gen_random_uuid(), o.id, o.quantity, o.price, 'SEED', o.created_on
  FROM orders o
 WHERE o.status = 'FILLED'
   AND o.idempotency_key LIKE 'seed-order-%'
ON CONFLICT (order_id) DO NOTHING;
