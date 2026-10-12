-- Instruments seed data
-- last_price is a starting market price; the API's simulated price feed moves it from there
INSERT INTO instruments (symbol, name, asset_class, currency, tradable, last_price, last_price_on) VALUES
    ('ACME',  'Acme Corp',            'Equity',     'USD',       TRUE,    26.10, NOW()),
    ('GLOB',  'Global Growth Fund',   'Fund',       'USD',       FALSE,   41.50, NOW()),
    ('BOND1', 'Corporate Bond Fund',  'Bond',       'USD',       TRUE,    40.20, NOW()),
    ('TECH',  'TechHub Inc',          'Equity',     'USD',       TRUE,    52.75, NOW()),
    ('GOOG',  'Google Inc',           'Equity',     'USD',       TRUE,   125.40, NOW()),
    ('APPL',  'Apple Inc',            'Equity',     'USD',       TRUE,   161.30, NOW()),
    ('MSFT',  'Microsoft Corp',       'Equity',     'USD',       TRUE,   305.80, NOW()),
    ('GOLD',  'Gold ETF',             'Commodity',  'USD',       TRUE,    66.10, NOW()),
    ('UTIL',  'Utility Sector Fund',  'Fund',       'USD',       TRUE,    18.45, NOW()),
    ('HEMP',  'Hemp Industries Fund', 'Fund',       'USD',       TRUE,     7.90, NOW());
