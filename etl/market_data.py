"""EXTRACT (prices): read daily price bars from the Alpaca market data API.

Only uses the standard library, so the container needs no extra packages.
"""

import json
import logging
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

import config

logger = logging.getLogger(__name__)

BARS_PATH = "/v2/stocks/bars"
PAGE_SIZE = 10000

# The free plan can't query the latest 15 minutes of SIP data
DATA_DELAY = timedelta(minutes=16)

# Rate limited (429) or an Alpaca server error: worth trying again
RETRY_STATUSES = {429, 500, 502, 503, 504}
REQUEST_ATTEMPTS = 3
REQUEST_TIMEOUT_SECONDS = 30


class MarketDataError(Exception):
    """The market data API could not be reached or refused the request."""


def is_configured():
    return bool(config.ALPACA_API_KEY_ID and config.ALPACA_API_SECRET_KEY)


def start_dates(last_loaded, today, backfill_years):
    """Work out where each symbol's download starts, grouped so each start is one request.

    last_loaded maps symbol -> the last day already loaded (or None if none).
    That day is downloaded again, in case it was loaded before the market closed.
    Symbols with nothing loaded yet start backfill_years ago.

    Returns {start date: [symbols]}.
    """
    backfill_start = today - timedelta(days=365 * backfill_years)
    groups = {}
    for symbol in sorted(last_loaded):
        start = last_loaded[symbol] or backfill_start
        groups.setdefault(start, []).append(symbol)
    return groups


def fetch_daily_bars(symbols, start, now=None):
    """Download daily bars for these symbols from start up to now.

    Returns {symbol: [bar, ...]}, each bar as Alpaca sends it, e.g.
    {"t": "2026-10-09T04:00:00Z", "o": 1.0, "h": 1.0, "l": 1.0, "c": 1.0, "v": 100, "n": 5, "vw": 1.0}.
    Prices are as traded (not adjusted for splits or dividends).
    """
    end = (now or datetime.now(timezone.utc)) - DATA_DELAY
    params = {
        "symbols": ",".join(symbols),
        "timeframe": "1Day",
        "start": start.isoformat(),
        "end": end.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "adjustment": "raw",
        "feed": config.PRICE_HISTORY_FEED,
        "limit": PAGE_SIZE,
        "sort": "asc",
    }

    bars = {}
    while True:
        page = _get(BARS_PATH, params)
        for symbol, symbol_bars in (page.get("bars") or {}).items():
            bars.setdefault(symbol, []).extend(symbol_bars)
        # Large requests come back in pages; the token asks for the next one
        token = page.get("next_page_token")
        if not token:
            break
        params["page_token"] = token

    logger.info("Fetched %d daily bars for %d symbols from %s",
                sum(len(b) for b in bars.values()), len(symbols), start)
    return bars


def _get(path, params):
    """GET a JSON response, trying again if Alpaca is busy or briefly unreachable."""
    url = f"{config.ALPACA_DATA_URL}{path}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={
        "APCA-API-KEY-ID": config.ALPACA_API_KEY_ID,
        "APCA-API-SECRET-KEY": config.ALPACA_API_SECRET_KEY,
        "Accept": "application/json",
    })

    delay = config.RETRY_DELAY_SECONDS
    for attempt in range(1, REQUEST_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code not in RETRY_STATUSES:
                # e.g. 401/403: wrong keys, or a feed the plan does not include
                raise MarketDataError(f"Alpaca returned {error.code}: {error.read().decode(errors='replace')}") from error
            problem = f"HTTP {error.code}"
        except (urllib.error.URLError, TimeoutError) as error:
            problem = str(getattr(error, "reason", error))

        if attempt == REQUEST_ATTEMPTS:
            raise MarketDataError(f"Alpaca request failed after {attempt} attempts: {problem}")
        logger.warning("Alpaca request failed (%s). Retrying in %.0fs...", problem, delay)
        time.sleep(delay)
        delay = delay * 2
