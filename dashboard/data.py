"""Loads the dashboard's two data sources from PostgreSQL.

- Batch: the analytics star schema the ETL pipeline loads once a day.
- Live: the trading tables, which the Kafka order consumer updates as orders
  are processed.

Both come back as DataFrames with the same trade columns, so the insight
functions work on either. Each trade carries the username of the account's
owner (users.account_id), or "(no login)" for accounts no user owns, such as
the seeded ones.
"""

import os
from pathlib import Path

import pandas as pd
import psycopg2
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

DB_SETTINGS = {
    "host": os.getenv("DASHBOARD_DB_HOST", "localhost"),
    "port": int(os.getenv("DASHBOARD_DB_PORT", "5432")),
    "dbname": os.getenv("DASHBOARD_DB_NAME", "tns_capital"),
    "user": os.getenv("DB_USER", "tns-capital-db-user"),
    "password": os.getenv("DB_PASSWORD", ""),
}

BATCH_TRADES_SQL = """
    SELECT f.order_id::text AS order_id, f.account_number, a.holder_name,
           COALESCE(u.username, '(no login)') AS username, f.symbol,
           i.asset_class, f.side, f.quantity, f.price, f.notional, f.status, f.created_on
    FROM analytics.fact_trades f
    JOIN analytics.dim_account a ON a.account_number = f.account_number
    JOIN analytics.dim_instrument i ON i.symbol = f.symbol
    LEFT JOIN accounts acc ON acc.account_number = f.account_number
    LEFT JOIN users u ON u.account_id = acc.id
"""

LIVE_TRADES_SQL = """
    SELECT o.id::text AS order_id, a.account_number, a.holder_name,
           COALESCE(u.username, '(no login)') AS username, o.symbol,
           i.asset_class, o.side, o.quantity, o.price, o.quantity * o.price AS notional,
           o.status, o.created_on
    FROM orders o
    JOIN accounts a ON a.id = o.account_id
    JOIN instruments i ON i.symbol = o.symbol
    LEFT JOIN users u ON u.account_id = a.id
"""

POSITIONS_SQL = """
    SELECT p.symbol, i.asset_class, p.quantity, p.average_cost
    FROM positions p
    JOIN instruments i ON i.symbol = p.symbol
"""

NUMERIC_COLUMNS = ["quantity", "price", "notional", "average_cost"]


def connect():
    return psycopg2.connect(**DB_SETTINGS)


def read(conn, sql):
    """Run a SELECT and return the rows as a DataFrame with numeric money columns."""
    with conn.cursor() as cur:
        cur.execute(sql)
        frame = pd.DataFrame(cur.fetchall(), columns=[col.name for col in cur.description])
    for column in NUMERIC_COLUMNS:
        if column in frame:
            frame[column] = pd.to_numeric(frame[column])
    return frame


def batch_available(conn):
    """True once the ETL has created the analytics schema."""
    with conn.cursor() as cur:
        cur.execute("SELECT to_regclass('analytics.fact_trades') IS NOT NULL")
        return cur.fetchone()[0]


def load_all():
    """Every DataFrame the dashboard shows. Batch frames are empty before the first ETL run."""
    with connect() as conn:
        live = read(conn, LIVE_TRADES_SQL)
        positions = read(conn, POSITIONS_SQL)
        has_batch = batch_available(conn)
        batch = read(conn, BATCH_TRADES_SQL) if has_batch else live.iloc[0:0]
    return {"live": live, "batch": batch, "positions": positions, "has_batch": has_batch}
