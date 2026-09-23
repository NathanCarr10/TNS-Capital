#!/usr/bin/env python3
"""
Yahoo Finance data fetcher using yfinance library
Better rate-limit handling than Java library
Usage: python3 fetch_yfinance.py SYMBOL START_DATE END_DATE [RETRIES]
Example: python3 fetch_yfinance.py AAPL 2024-09-01 2024-09-30 3
"""

import yfinance as yf
import pandas as pd
import json
import sys
import time
import warnings
from datetime import datetime

# Suppress yfinance debug warnings
warnings.filterwarnings('ignore')

def fetch_stock_data(symbol, start_date, end_date, max_retries=3):
    """
    Fetch stock data with exponential backoff retry logic
    
    Args:
        symbol: Stock ticker (e.g., 'AAPL')
        start_date: Start date as YYYY-MM-DD
        end_date: End date as YYYY-MM-DD
        max_retries: Maximum retry attempts
    
    Returns:
        List of price history dicts
    """
    for attempt in range(1, max_retries + 1):
        try:
            print(f"[Attempt {attempt}/{max_retries}] Fetching {symbol} from {start_date} to {end_date}...", file=sys.stderr)
            
            # Download data with progress disabled
            data = yf.download(symbol, start=start_date, end=end_date, progress=False)
            
            if data.empty:
                print(f"[WARN] No data returned for {symbol}", file=sys.stderr)
                return []
            
            # Convert to our format
            # Handle both single symbol and multiple symbol results
            if isinstance(data.columns, pd.MultiIndex):
                # Multiple columns result (symbol in tuple)
                result = []
                for date, row in data.iterrows():
                    result.append({
                        "symbol": symbol,
                        "date": date.strftime('%Y-%m-%d'),
                        "open_price": float(row.get(('Open', symbol), 0)),
                        "high_price": float(row.get(('High', symbol), 0)),
                        "low_price": float(row.get(('Low', symbol), 0)),
                        "close_price": float(row.get(('Close', symbol), 0)),
                        "adj_close_price": float(row.get(('Adj Close', symbol), row.get(('Close', symbol), 0))),
                        "volume": int(row.get(('Volume', symbol), 0))
                    })
            else:
                # Single symbol result (simple columns)
                result = []
                for date, row in data.iterrows():
                    result.append({
                        "symbol": symbol,
                        "date": date.strftime('%Y-%m-%d'),
                        "open_price": float(row['Open']) if 'Open' in data.columns else 0,
                        "high_price": float(row['High']) if 'High' in data.columns else 0,
                        "low_price": float(row['Low']) if 'Low' in data.columns else 0,
                        "close_price": float(row['Close']) if 'Close' in data.columns else 0,
                        "adj_close_price": float(row['Adj Close']) if 'Adj Close' in data.columns else float(row['Close']),
                        "volume": int(row['Volume']) if 'Volume' in data.columns else 0
                    })
            
            print(f"[SUCCESS] Fetched {len(result)} records for {symbol}", file=sys.stderr)
            return result
            
        except Exception as e:
            error_msg = str(e)
            is_rate_limited = "429" in error_msg or "rate" in error_msg.lower()
            
            if is_rate_limited and attempt < max_retries:
                wait_time = 5 * (2 ** (attempt - 1))  # 5s, 10s, 20s
                print(f"[WARN] Rate limited (HTTP 429). Waiting {wait_time}s before retry...", file=sys.stderr)
                time.sleep(wait_time)
            else:
                print(f"[ERROR] Failed to fetch {symbol}: {error_msg}", file=sys.stderr)
                if attempt == max_retries:
                    return {"error": error_msg, "symbol": symbol, "attempts": max_retries}
    
    return {"error": "Max retries exceeded", "symbol": symbol}

def main():
    if len(sys.argv) < 4:
        print(json.dumps({
            "error": "Usage: fetch_yfinance.py SYMBOL START_DATE END_DATE [RETRIES]",
            "example": "fetch_yfinance.py AAPL 2024-09-01 2024-09-30 3"
        }))
        sys.exit(1)
    
    symbol = sys.argv[1]
    start_date = sys.argv[2]
    end_date = sys.argv[3]
    max_retries = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    
    try:
        result = fetch_stock_data(symbol, start_date, end_date, max_retries)
        print(json.dumps(result))
    except Exception as e:
        print(json.dumps({
            "error": str(e),
            "symbol": symbol,
            "type": "unexpected_error"
        }))
        sys.exit(1)

if __name__ == "__main__":
    main()
