-- Instruments seed data
-- Real US-listed tickers, so the market data feed (Alpaca) can price them.
-- The free Alpaca plan streams live prices for up to 30 symbols.
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    -- Equities
    ('AAPL',  'Apple Inc',                                  'Equity',     'USD',  TRUE),
    ('MSFT',  'Microsoft Corp',                             'Equity',     'USD',  TRUE),
    ('GOOGL', 'Alphabet Inc Class A',                       'Equity',     'USD',  TRUE),
    ('AMZN',  'Amazon.com Inc',                             'Equity',     'USD',  TRUE),
    ('NVDA',  'NVIDIA Corp',                                'Equity',     'USD',  TRUE),
    ('META',  'Meta Platforms Inc',                         'Equity',     'USD',  TRUE),
    ('TSLA',  'Tesla Inc',                                  'Equity',     'USD',  TRUE),
    ('NFLX',  'Netflix Inc',                                'Equity',     'USD',  TRUE),
    ('AMD',   'Advanced Micro Devices Inc',                 'Equity',     'USD',  TRUE),
    ('INTC',  'Intel Corp',                                 'Equity',     'USD',  TRUE),
    ('JPM',   'JPMorgan Chase & Co',                        'Equity',     'USD',  TRUE),
    ('BAC',   'Bank of America Corp',                       'Equity',     'USD',  TRUE),
    ('V',     'Visa Inc',                                   'Equity',     'USD',  TRUE),
    ('JNJ',   'Johnson & Johnson',                          'Equity',     'USD',  TRUE),
    ('PFE',   'Pfizer Inc',                                 'Equity',     'USD',  TRUE),
    ('WMT',   'Walmart Inc',                                'Equity',     'USD',  TRUE),
    ('PG',    'Procter & Gamble Co',                        'Equity',     'USD',  TRUE),
    ('KO',    'Coca-Cola Co',                               'Equity',     'USD',  TRUE),
    ('DIS',   'Walt Disney Co',                             'Equity',     'USD',  TRUE),
    ('XOM',   'Exxon Mobil Corp',                           'Equity',     'USD',  TRUE),
    -- Funds (ETFs)
    ('SPY',   'SPDR S&P 500 ETF Trust',                     'Fund',       'USD',  TRUE),
    ('QQQ',   'Invesco QQQ Trust',                          'Fund',       'USD',  TRUE),
    ('VTI',   'Vanguard Total Stock Market ETF',            'Fund',       'USD',  TRUE),
    ('IWM',   'iShares Russell 2000 ETF',                   'Fund',       'USD',  TRUE),
    ('XLU',   'Utilities Select Sector SPDR Fund',          'Fund',       'USD',  TRUE),
    ('ARKK',  'ARK Innovation ETF',                         'Fund',       'USD',  FALSE),
    -- Bonds (bond ETFs)
    ('TLT',   'iShares 20+ Year Treasury Bond ETF',         'Bond',       'USD',  TRUE),
    ('LQD',   'iShares iBoxx $ Investment Grade Corp Bond', 'Bond',       'USD',  TRUE),
    -- Commodities (commodity ETFs)
    ('GLD',   'SPDR Gold Shares',                           'Commodity',  'USD',  TRUE),
    ('SLV',   'iShares Silver Trust',                       'Commodity',  'USD',  TRUE);
