-- Holdings per account with instrument detail, for dashboards and reporting.
CREATE OR REPLACE VIEW v_positions_detail AS
SELECT
    a.account_number,
    a.holder_name,
    p.symbol,
    i.name          AS instrument_name,
    i.asset_class,
    i.currency,
    p.quantity,
    p.average_cost,
    (p.quantity * p.average_cost) AS cost_basis_value
FROM positions p
JOIN accounts a     ON a.id = p.account_id
JOIN instruments i  ON i.symbol = p.symbol;
