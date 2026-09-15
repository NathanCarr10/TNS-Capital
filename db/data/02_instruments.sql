-- Instruments seed data
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('ACME',  'Acme Corp',           'Equity', 'USD', TRUE),
    ('GLOB',  'Global Growth Fund',  'Fund',   'USD', TRUE),
    ('BOND1', 'Corporate Bond Fund', 'Bond',   'USD', TRUE);
