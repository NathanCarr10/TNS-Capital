-- Per-account summary of cash, holdings value and total account value.
CREATE OR REPLACE VIEW v_account_summary AS
SELECT
    a.account_number,
    a.holder_name,
    a.status,
    a.cash_balance,
    COALESCE(SUM(p.quantity * p.average_cost), 0)                     AS holdings_value,
    a.cash_balance + COALESCE(SUM(p.quantity * p.average_cost), 0)    AS total_account_value
FROM accounts a
LEFT JOIN positions p ON p.account_id = a.id
GROUP BY a.account_number, a.holder_name, a.status, a.cash_balance;
