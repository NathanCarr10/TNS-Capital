"""Load stage: upsert the transformed star schema into the analytics schema.

The whole load runs in one transaction, so a failure part-way leaves the
analytics tables exactly as they were. Every write is an upsert keyed on a
natural key (account_id, symbol, date_key, order_id), so re-running the same
data changes nothing. Together these make a failed or repeated run safe to
retry.
"""

import logging
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List, Sequence

import numpy as np
import pandas as pd
from psycopg2.extras import execute_values

from tns_etl.transform import TransformResult

logger = logging.getLogger(__name__)

SCHEMA_SQL = Path(__file__).with_name("schema.sql").read_text()

# Arbitrary constant; serialises concurrent loads (e.g. a manual run during a scheduled one).
ADVISORY_LOCK_KEY = 7363001


@dataclass
class LoadStats:
    """Rows inserted or changed per table. A repeat run of unchanged data reports zeros."""

    dim_account: int = 0
    dim_instrument: int = 0
    dim_date: int = 0
    fact_trades: int = 0

    def as_dict(self) -> dict:
        return asdict(self)


def ensure_schema(cur, schema: str) -> None:
    cur.execute(SCHEMA_SQL.replace("{schema}", schema))


def load(conn, result: TransformResult, schema: str) -> LoadStats:
    with conn:  # commits on success, rolls back on any exception
        with conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_xact_lock(%s)", (ADVISORY_LOCK_KEY,))
            ensure_schema(cur, schema)
            stats = LoadStats(
                dim_account=_upsert(cur, f"{schema}.dim_account", "account_id",
                                    result.dim_account,
                                    ["account_id", "holder_name", "account_status"]),
                dim_instrument=_upsert(cur, f"{schema}.dim_instrument", "symbol",
                                       result.dim_instrument,
                                       ["symbol", "instrument_name", "asset_class",
                                        "currency", "tradable"]),
                dim_date=_upsert(cur, f"{schema}.dim_date", "date_key", result.dim_date,
                                 ["date_key", "full_date", "year", "quarter", "month",
                                  "month_name", "day", "day_of_week", "day_name",
                                  "is_weekend"]),
                fact_trades=_upsert_fact_trades(cur, schema, result.fact_trades),
            )
    logger.info("Loaded into %s (rows inserted or changed): %s", schema, stats.as_dict())
    return stats


def _upsert(cur, table: str, key: str, df: pd.DataFrame, columns: Sequence[str]) -> int:
    """INSERT ... ON CONFLICT DO UPDATE, touching only rows whose values changed."""
    if df.empty:
        return 0
    updates = [c for c in columns if c != key]
    has_loaded_at = not table.endswith(".dim_date")
    set_clause = ", ".join(f"{c} = EXCLUDED.{c}" for c in updates)
    if has_loaded_at:
        set_clause += ", loaded_at = NOW()"
    target = table.split(".")[-1]
    sql = f"""
        INSERT INTO {table} ({", ".join(columns)}) VALUES %s
        ON CONFLICT ({key}) DO UPDATE SET {set_clause}
        WHERE ({", ".join(f"{target}.{c}" for c in updates)})
              IS DISTINCT FROM ({", ".join(f"EXCLUDED.{c}" for c in updates)})
        RETURNING 1
    """
    # fetch=True gathers RETURNING rows from every page, so the count is exact.
    return len(execute_values(cur, sql, _rows(df, columns), fetch=True))


FACT_STAGE_COLUMNS = ["order_id", "account_id", "symbol", "date_key", "side", "quantity",
                      "signed_quantity", "price", "notional", "status", "is_filled",
                      "created_on", "idempotency_key"]
FACT_MEASURES = ["account_key", "instrument_key", "date_key", "side", "quantity",
                 "signed_quantity", "price", "notional", "status", "is_filled",
                 "created_on", "idempotency_key"]


def _upsert_fact_trades(cur, schema: str, fact: pd.DataFrame) -> int:
    """Stage facts in a temp table, then resolve dimension keys and upsert in SQL."""
    if fact.empty:
        return 0
    cur.execute("""
        CREATE TEMP TABLE stage_fact_trades (
            order_id UUID, account_id VARCHAR(32), symbol VARCHAR(20), date_key INT,
            side VARCHAR(4), quantity INT, signed_quantity INT, price NUMERIC(18,2),
            notional NUMERIC(20,2), status VARCHAR(20), is_filled BOOLEAN,
            created_on TIMESTAMP, idempotency_key VARCHAR(100)
        ) ON COMMIT DROP
    """)
    execute_values(cur, f"INSERT INTO stage_fact_trades ({', '.join(FACT_STAGE_COLUMNS)}) VALUES %s",
                   _rows(fact, FACT_STAGE_COLUMNS))
    cur.execute(f"""
        INSERT INTO {schema}.fact_trades (order_id, {", ".join(FACT_MEASURES)})
        SELECT st.order_id, da.account_key, di.instrument_key, st.date_key, st.side,
               st.quantity, st.signed_quantity, st.price, st.notional, st.status,
               st.is_filled, st.created_on, st.idempotency_key
        FROM stage_fact_trades st
        JOIN {schema}.dim_account da ON da.account_id = st.account_id
        JOIN {schema}.dim_instrument di ON di.symbol = st.symbol
        ON CONFLICT (order_id) DO UPDATE SET
            {", ".join(f"{c} = EXCLUDED.{c}" for c in FACT_MEASURES)}, loaded_at = NOW()
        WHERE ({", ".join(f"fact_trades.{c}" for c in FACT_MEASURES)})
              IS DISTINCT FROM ({", ".join(f"EXCLUDED.{c}" for c in FACT_MEASURES)})
    """)
    return cur.rowcount


def _rows(df: pd.DataFrame, columns: Sequence[str]) -> List[tuple]:
    """DataFrame rows as tuples of plain Python values psycopg2 can adapt."""
    return [tuple(_to_python(v) for v in row)
            for row in df[list(columns)].itertuples(index=False, name=None)]


def _to_python(value):
    if value is None or value is pd.NA or value is pd.NaT:
        return None
    if isinstance(value, pd.Timestamp):
        return value.to_pydatetime()
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, float) and np.isnan(value):
        return None
    return value
