-- The archived copy of each cancelled seed order, as the trading API keeps one
-- for every order it cancels (GET /api/v1/orders/history/{orderId})
INSERT INTO order_history (order_id, account_id, symbol, side, quantity, price, status, idempotency_key,
                           order_created_on, deleted_on)
SELECT o.id, o.account_id, o.symbol, o.side, o.quantity, o.price, o.status, o.idempotency_key,
       o.created_on, o.created_on + INTERVAL '1 second'
  FROM orders o
 WHERE o.status = 'CANCELLED'
   AND o.idempotency_key LIKE 'seed-key-%'
ON CONFLICT (idempotency_key) DO NOTHING;
