-- Instruments seed data
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('ACME',  'Acme Corp',            'Equity', 'USD', TRUE),
    ('GLOB',  'Global Growth Fund',   'Fund',   'USD', TRUE),
    ('BOND1', 'Corporate Bond Fund',  'Bond',   'USD', TRUE),
    ('TECH',  'TechHub Inc',          'Equity', 'USD', TRUE),
    ('GOOG',  'Google Inc',           'Equity', 'USD', TRUE),
    ('APPL',  'Apple Inc',            'Equity', 'USD', TRUE),
    ('MSFT',  'Microsoft Corp',       'Equity', 'USD', TRUE),
    ('GOLD',  'Gold ETF',             'Commodity', 'USD', TRUE),
    ('UTIL',  'Utility Sector Fund',  'Fund',   'USD', TRUE),
    ('HEMP',  'Hemp Industries Fund', 'Fund',   'USD', TRUE);
