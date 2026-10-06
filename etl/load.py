"""LOAD: save the reporting tables into the 'analytics' schema in PostgreSQL."""

import logging
from pathlib import Path

from psycopg2.extras import execute_values

logger = logging.getLogger(__name__)

SCHEMA_SQL = (Path(__file__).parent / "schema.sql").read_text()

# The column that uniquely identifies a row in each table
PRIMARY_KEYS = {
    "dim_account": "account_number",
    "dim_instrument": "symbol",
    "dim_date": "date_key",
    "fact_trades": "order_id",
}


def upsert(cur, table, df):
    """Insert rows, or update them if a row with the same key already exists.

    This is what makes the pipeline repeatable: running it twice on the same
    data updates the same rows instead of adding duplicates.
    """
    if df.empty:
        return
    key = PRIMARY_KEYS[table]
    columns = list(df.columns)
    updates = ", ".join(f"{col} = EXCLUDED.{col}" for col in columns if col != key)

    sql = f"""
        INSERT INTO analytics.{table} ({", ".join(columns)})
        VALUES %s
        ON CONFLICT ({key}) DO UPDATE SET {updates}
    """
    # Convert pandas/numpy values to plain Python values (and NaN to None)
    rows = df.astype(object).where(df.notna(), None).values.tolist()
    execute_values(cur, sql, rows)


def load(conn, tables):
    # "with conn" makes this one transaction: either every table is saved,
    # or (if anything fails) nothing is saved at all.
    with conn:
        with conn.cursor() as cur:
            cur.execute(SCHEMA_SQL)
            # Dimensions first, because fact_trades refers to them
            for table in ["dim_account", "dim_instrument", "dim_date", "fact_trades"]:
                upsert(cur, table, tables[table])
                logger.info("Loaded %d rows into analytics.%s", len(tables[table]), table)

            replace_rejects(cur, tables.get("etl_rejected_orders"))


def replace_rejects(cur, rejects):
    """Replace the rejected-orders table with this run's rejects.

    It always shows the rows that failed the latest run: a row fixed at the
    source disappears from it on the next run.
    """
    cur.execute("DELETE FROM analytics.etl_rejected_orders")
    if rejects is None or rejects.empty:
        return
    columns = list(rejects.columns)
    rows = rejects.astype(object).where(rejects.notna(), None).values.tolist()
    execute_values(cur, f"INSERT INTO analytics.etl_rejected_orders ({', '.join(columns)}) VALUES %s", rows)
    logger.warning("%d orders failed validation; see analytics.etl_rejected_orders", len(rejects))
