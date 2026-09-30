"""EXTRACT: read the trading data out of PostgreSQL into pandas DataFrames."""

import logging

import pandas as pd

logger = logging.getLogger(__name__)

ACCOUNTS_SQL = "SELECT account_number AS account_id, holder_name, status FROM accounts"

INSTRUMENTS_SQL = "SELECT symbol, name, asset_class, currency FROM instruments"

# orders.account_id holds the account's database id (e.g. 1), so we join to
# accounts to get the readable account identifier (e.g. 'ACC-1001') instead.
ORDERS_SQL = """
    SELECT o.id::text AS order_id,
           a.id AS account_id,
           o.symbol,
           o.side,
           o.quantity,
           o.price,
           o.status,
           o.created_on
    FROM orders o
    JOIN accounts a ON a.id = o.account_id
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
