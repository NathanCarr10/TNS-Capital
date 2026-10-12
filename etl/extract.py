"""EXTRACT: read the trading data out of PostgreSQL into pandas DataFrames."""

import logging

import pandas as pd

logger = logging.getLogger(__name__)

ACCOUNTS_SQL = "SELECT account_number, holder_name, status FROM accounts"

INSTRUMENTS_SQL = "SELECT symbol, name, asset_class, currency FROM instruments"

# orders.account_id holds the account's database id (e.g. 1), so we join to
# accounts to get the account number (e.g. 'ACC-1001') instead.
ORDERS_SQL = """
    SELECT o.id::text AS order_id,
           a.account_number,
           o.symbol,
           o.side,
           o.quantity,
           o.price,
           o.status,
           o.created_on
    FROM orders o
    JOIN accounts a ON a.id = o.account_id
"""


INSTRUMENT_SYMBOLS_SQL = "SELECT symbol FROM instruments ORDER BY symbol"

LAST_PRICE_DATES_SQL = """
    SELECT symbol, MAX(trade_date)
    FROM analytics.fact_daily_prices
    GROUP BY symbol
"""


def read_table(conn, sql):
    """Run a SELECT and return the rows as a DataFrame."""
    with conn.cursor() as cur:
        cur.execute(sql)
        columns = [col.name for col in cur.description]
        return pd.DataFrame(cur.fetchall(), columns=columns)


def extract(conn):
    accounts = read_table(conn, ACCOUNTS_SQL)
    instruments = read_table(conn, INSTRUMENTS_SQL)
    orders = read_table(conn, ORDERS_SQL)

    logger.info("Extracted %d accounts, %d instruments, %d orders",
                len(accounts), len(instruments), len(orders))
    return accounts, instruments, orders


def extract_last_price_dates(conn):
    """For each instrument, the last day of prices already loaded (None if none yet)."""
    with conn.cursor() as cur:
        cur.execute(INSTRUMENT_SYMBOLS_SQL)
        symbols = [row[0] for row in cur.fetchall()]

        # On the first run the prices table does not exist yet
        cur.execute("SELECT to_regclass('analytics.fact_daily_prices')")
        if cur.fetchone()[0] is None:
            last_loaded = {}
        else:
            cur.execute(LAST_PRICE_DATES_SQL)
            last_loaded = dict(cur.fetchall())

    return {symbol: last_loaded.get(symbol) for symbol in symbols}
