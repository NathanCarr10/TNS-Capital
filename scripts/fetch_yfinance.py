#!/usr/bin/env python3
"""Yahoo Finance data fetcher using yfinance library with exponential backoff retry logic."""

import yfinance as yf
import pandas as pd
import json
import sys
import time
import random
import warnings

warnings.filterwarnings('ignore')

def fetch_stock_data(symbol, start_date, end_date, max_retries=3):
    def safe_float(value, default=0.0):
        try:
            val = float(value)
            return default if pd.isna(val) else val
        except (ValueError, TypeError):
            return default
    
    def safe_int(value, default=0):
        try:
            val = int(value)
            return default if pd.isna(val) else val
        except (ValueError, TypeError):
            return default
    
    for attempt in range(1, max_retries + 1):
        try:
            data = yf.download(symbol, start=start_date, end=end_date, progress=False)
            
            if data is None or (hasattr(data, 'empty') and data.empty) or len(data) == 0:
                return []
            
            result = []
            try:
                if isinstance(data.columns, pd.MultiIndex):
                    for date_value, row in data.iterrows():
                        try:
                            date_str = pd.Timestamp(date_value).strftime('%Y-%m-%d')
                        except Exception:
                            date_str = str(date_value)[:10]
                        result.append({
                            "symbol": symbol,
                            "date": date_str,
                            "open_price": safe_float(row.get(('Open', symbol), 0)),
                            "high_price": safe_float(row.get(('High', symbol), 0)),
                            "low_price": safe_float(row.get(('Low', symbol), 0)),
                            "close_price": safe_float(row.get(('Close', symbol), 0)),
                            "adj_close_price": safe_float(row.get(('Adj Close', symbol), row.get(('Close', symbol), 0))),
                            "volume": safe_int(row.get(('Volume', symbol), 0))
                        })
                else:
                    for date_value, row in data.iterrows():
                        try:
                            date_str = pd.Timestamp(date_value).strftime('%Y-%m-%d')
                        except Exception:
                            date_str = str(date_value)[:10]
                        result.append({
                            "symbol": symbol,
                            "date": date_str,
                            "open_price": safe_float(row.get('Open', 0) if 'Open' in data.columns else 0),
                            "high_price": safe_float(row.get('High', 0) if 'High' in data.columns else 0),
                            "low_price": safe_float(row.get('Low', 0) if 'Low' in data.columns else 0),
                            "close_price": safe_float(row.get('Close', 0) if 'Close' in data.columns else 0),
                            "adj_close_price": safe_float(row.get('Adj Close', row.get('Close', 0)) if 'Adj Close' in data.columns or 'Close' in data.columns else 0),
                            "volume": safe_int(row.get('Volume', 0) if 'Volume' in data.columns else 0)
                        })
            except Exception:
                return []
            
            return result
            
        except Exception as e:
            error_msg = str(e)
            is_rate_limited = "429" in error_msg or "rate" in error_msg.lower()
            
            if is_rate_limited and attempt < max_retries:
                base_wait = 5 * (2 ** (attempt - 1))
                jitter = random.uniform(0.8, 1.2)
                wait_time = base_wait * jitter
                time.sleep(wait_time)
            else:
                if attempt == max_retries:
                    return [{"error": error_msg, "symbol": symbol}]
    
    return [{"error": "Max retries exceeded", "symbol": symbol}]

def main():
    if len(sys.argv) < 4:
        result = [{"error": "Usage: fetch_yfinance.py SYMBOL START_DATE END_DATE [MAX_RETRIES]"}]
        print(json.dumps(result))
        return
    
    symbol = sys.argv[1]
    start_date = sys.argv[2]
    end_date = sys.argv[3]
    max_retries = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    
    try:
        result = fetch_stock_data(symbol, start_date, end_date, max_retries)
        print(json.dumps(result))
    except Exception as e:
        result = [{"error": str(e), "symbol": symbol}]
        print(json.dumps(result))

if __name__ == "__main__":
    main()
