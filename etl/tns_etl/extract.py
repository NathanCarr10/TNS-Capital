"""Extract stage: read operational trade data from the TNS Capital PostgreSQL database.

Extraction is deliberately free of business logic; it returns the source
tables as-is so every rule lives in (and is tested with) the transform stage.
"""

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Optional

import pandas as pd

logger = logging.getLogger(__name__)

ACCOUNTS_SQL = """
    SELECT id AS account_pk, account_id, holder_name, status
    FROM accounts
    ORDER BY id
"""

INSTRUMENTS_SQL = """
    SELECT symbol, name, asset_class, currency, tradable
    FROM instruments
    ORDER BY symbol
"""

# Orders have no updated_at column and their status changes after creation
# (NEW -> FILLED/CANCELLED), so the window filters on created_on only and
# the load stage upserts, which keeps re-extracting the same window safe.
ORDERS_SQL = """
    SELECT id::text AS order_id, account_id AS account_pk, symbol, side, quantity,
           price, status, idempotency_key, created_on
    FROM orders
    WHERE (%(since)s IS NULL OR created_on >= %(since)s)
      AND (%(until)s IS NULL OR created_on < %(until)s)
    ORDER BY created_on, id
"""


@dataclass
class ExtractResult:
    accounts: pd.DataFrame
    instruments: pd.DataFrame
    orders: pd.DataFrame

    def row_count(self) -> int:
        return len(self.accounts) + len(self.instruments) + len(self.orders)


def _query(conn, sql: str, params: Optional[dict] = None) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(sql, params)
        columns = [col.name for col in cur.description]
        return pd.DataFrame.from_records(cur.fetchall(), columns=columns)


def extract(conn, since: Optional[datetime] = None, until: Optional[datetime] = None) -> ExtractResult:
    """Read accounts, instruments and orders (optionally a created_on window) in one snapshot."""
    # REPEATABLE READ gives all three queries the same snapshot, so an order
    # can't reference an account inserted between the queries.
    conn.set_session(isolation_level="REPEATABLE READ", readonly=True)
    try:
        result = ExtractResult(
            accounts=_query(conn, ACCOUNTS_SQL),
            instruments=_query(conn, INSTRUMENTS_SQL),
            orders=_query(conn, ORDERS_SQL, {"since": since, "until": until}),
        )
    finally:
        conn.rollback()
    logger.info("Extracted %d accounts, %d instruments, %d orders (window %s -> %s)",
                len(result.accounts), len(result.instruments), len(result.orders),
                since or "start", until or "now")
    return result
