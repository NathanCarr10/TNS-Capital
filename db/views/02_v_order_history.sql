-- Order blotter: orders with account, instrument and fill detail, most recent first.
CREATE OR REPLACE VIEW v_order_history AS
SELECT
    o.id                    AS order_id,
    a.account_number,
    a.holder_name,
    o.symbol,
    i.name                  AS instrument_name,
    o.side,
    o.quantity,
    o.price,
    (o.quantity * o.price)  AS order_value,
    o.status,
    o.status_reason,
    e.price                 AS fill_price,
    e.venue                 AS fill_venue,
    e.executed_on,
    o.created_on
FROM orders o
JOIN accounts a         ON a.id = o.account_id
JOIN instruments i      ON i.symbol = o.symbol
LEFT JOIN executions e  ON e.order_id = o.id
ORDER BY o.created_on DESC;
