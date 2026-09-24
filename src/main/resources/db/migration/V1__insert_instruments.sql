-- Test if Flyway is running
-- This comment should appear in logs if Flyway executes this file

-- Instruments seed data
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('ACME',  'Acme Corp',            'EQUITY', 'USD', TRUE),
    ('GLOB',  'Global Growth Fund',   'FUND',   'USD', TRUE),
    ('BOND1', 'Corporate Bond Fund',  'BOND',   'USD', TRUE),
    ('TECH',  'TechHub Inc',          'EQUITY', 'USD', TRUE),
    ('GOOG',  'Google Inc',           'EQUITY', 'USD', TRUE),
    ('APPL',  'Apple Inc',            'EQUITY', 'USD', TRUE),
    ('MSFT',  'Microsoft Corp',       'EQUITY', 'USD', TRUE),
    ('GOLD',  'Gold ETF',             'COMMODITY', 'USD', TRUE),
    ('UTIL',  'Utility Sector Fund',  'FUND',   'USD', TRUE),
    ('HEMP',  'Hemp Industries Fund', 'FUND',   'USD', TRUE);
