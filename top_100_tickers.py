"""
Top 100 Most Traded Global Instruments
=======================================

This list contains the top 100 most traded instruments globally by daily average
trading volume. It includes stocks from major exchanges worldwide and is formatted
for use with yfinance library.

Ticker Format:
- US stocks: AAPL, MSFT (no suffix)
- UK stocks: SHELL.L, ASML.AS (suffix indicates exchange)
- European stocks: SAP.DE, SIEMENS.DE (suffix for exchange)
- Asian stocks: 0700.HK (Hong Kong), 005930.KS (South Korea), 6998.T (Tokyo)
- Other: .AX for Australia, .TO for Canada, etc.

Coverage by Region:
- US stocks: ~55 tickers (NYSE/NASDAQ)
- European stocks: ~20 tickers
- Asian stocks: ~15 tickers
- Other regions: ~10 tickers

Last Updated: 2026
"""

top_100_tickers = [
    # US Large-Cap Stocks (Most Traded by Volume)
    "AAPL",      # Apple
    "MSFT",      # Microsoft
    "AMZN",      # Amazon
    "GOOGL",     # Alphabet (Google)
    "GOOG",      # Alphabet (Google) - Class C
    "BRK.B",     # Berkshire Hathaway Class B
    "NVDA",      # NVIDIA
    "TSLA",      # Tesla
    "META",      # Meta Platforms (Facebook)
    "AVGO",      # Broadcom
    "AMD",       # Advanced Micro Devices
    "COST",      # Costco
    "NFLX",      # Netflix
    "QCOM",      # Qualcomm
    "ADBE",      # Adobe
    "INTC",      # Intel
    "CSCO",      # Cisco
    "CRM",       # Salesforce
    "ACN",       # Accenture
    "ORCL",      # Oracle
    "IBM",       # IBM
    "PYPL",      # PayPal
    "UBER",      # Uber
    "ABNB",      # Airbnb
    "TTWO",      # Take-Two Interactive
    "EA",        # Electronic Arts
    "GME",       # GameStop
    "AMC",       # AMC Entertainment
    "XOM",       # ExxonMobil
    "CVX",       # Chevron
    "MPC",       # Marathon Petroleum
    "PSX",       # Phillips 66
    "HES",       # Hess
    "COP",       # ConocoPhillips
    "JPM",       # JPMorgan Chase
    "BAC",       # Bank of America
    "WFC",       # Wells Fargo
    "GS",        # Goldman Sachs
    "MS",        # Morgan Stanley
    "C",         # Citigroup
    "BLK",       # BlackRock
    "SCHW",      # Charles Schwab
    "ICE",       # Intercontinental Exchange
    "JNJ",       # Johnson & Johnson
    "UNH",       # UnitedHealth
    "PFE",       # Pfizer
    "MRNA",      # Moderna
    "BNTX",      # BioNTech
    "LLY",       # Eli Lilly
    "MRK",       # Merck
    "ABBV",      # AbbVie
    "MA",        # Mastercard
    "V",         # Visa
    "AXP",       # American Express
    "DIS",       # Disney
    
    # US Mid-Cap & Other Highly Traded
    "COIN",      # Coinbase
    "MSTR",      # MicroStrategy
    "HOOD",      # Robinhood
    
    # European Stocks (with exchange codes)
    "SAP.DE",           # SAP (Germany)
    "ASML.AS",          # ASML (Netherlands)
    "SIEMENS.DE",       # Siemens (Germany)
    "SHELL.L",          # Shell (UK)
    "HSBA.L",           # HSBC (UK)
    "AZN.L",            # AstraZeneca (UK)
    "GSK.L",            # GlaxoSmithKline (UK)
    "ULVR.L",           # Unilever (UK)
    "ROLO.L",           # Rolls-Royce (UK)
    "BNZL.L",           # Bunzl (UK)
    "VOW3.DE",          # Volkswagen (Germany)
    "BMW.DE",           # BMW (Germany)
    "DAI.DE",           # Daimler (Germany)
    "DTE.DE",           # Deutsche Telekom (Germany)
    "BAS.DE",           # BASF (Germany)
    "OR.PA",            # L'Oreal (France)
    "BNPP.PA",          # BNP Paribas (France)
    "ACA.PA",           # Credit Agricole (France)
    "NOKIA.HE",         # Nokia (Finland)
    "ASEA.ST",          # ABB (Sweden)
    
    # Asian Stocks (with market codes)
    "0700.HK",          # Tencent (Hong Kong)
    "0005.HK",          # HSBC Holdings (Hong Kong)
    "0941.HK",          # China Mobile (Hong Kong)
    "005930.KS",        # Samsung Electronics (South Korea)
    "035420.KS",        # NAVER (South Korea)
    "6998.T",           # Sumitomo Mitsui (Japan)
    "8306.T",           # Mitsubishi UFJ (Japan)
    "9984.T",           # Softbank Group (Japan)
    "6752.T",           # Panasonic (Japan)
    "7203.T",           # Toyota Motor (Japan)
    "1299.HK",          # AIA Group (Hong Kong)
    "3988.HK",          # Bank of China (Hong Kong)
    "6328.T",           # Daiwa Securities (Japan)
    
    # Australian/Canadian/Other
    "BHP.AX",           # BHP Group (Australia)
    "CBA.AX",           # Commonwealth Bank (Australia)
    "WBC.AX",           # Westpac Banking (Australia)
    "TD.TO",            # Toronto-Dominion Bank (Canada)
    "RY.TO",            # Royal Bank of Canada (Canada)
    "BCE.TO",           # BCE Inc (Canada)
    "ENB.TO",           # Enbridge (Canada)
    "CNQ.TO",           # Canadian Natural (Canada)
    "TSM",              # Taiwan Semiconductor (US-listed ADR)
]

# Verify list length
assert len(top_100_tickers) == 100, f"List contains {len(top_100_tickers)} tickers, expected 100"

# Example usage with yfinance
if __name__ == "__main__":
    """
    Example: Download data for top 10 tickers
    
    Install yfinance first:
    pip install yfinance pandas
    
    Then use like this:
    
    import yfinance as yf
    from top_100_tickers import top_100_tickers
    
    # Download last 1 year of data for all tickers
    data = yf.download(top_100_tickers, start='2025-09-09', end='2026-09-09')
    
    # Or download for specific tickers
    data = yf.download(top_100_tickers[:10], period='1y')
    
    # Get current price
    ticker = yf.Ticker("AAPL")
    print(ticker.info['currentPrice'])
    """
    print(f"Loaded {len(top_100_tickers)} tickers")
    print(f"First 10: {top_100_tickers[:10]}")
    print(f"Last 10: {top_100_tickers[-10:]}")
