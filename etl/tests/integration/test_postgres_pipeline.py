"""End-to-end run against a real PostgreSQL database.

DESTRUCTIVE: recreates the source tables from db/ and drops the analytics
schema, so point it only at a throwaway database, e.g.

    docker run -d --rm --name etl-it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
    ETL_IT_DB_NAME=postgres ETL_DB_PORT=55432 DB_USER=postgres DB_PASSWORD=it \
        python -m pytest -m integration
"""

import os
from pathlib import Path

import psycopg2
import pytest

from tns_etl.config import EtlConfig, RetryPolicy
from tns_etl.pipeline import EtlPipeline

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(not os.environ.get("ETL_IT_DB_NAME"),
                       reason="set ETL_IT_DB_NAME to a disposable database to run"),
]

DB_DIR = Path(__file__).resolve().parents[3] / "db"


@pytest.fixture
def config():
    env = dict(os.environ, ETL_DB_NAME=os.environ.get("ETL_IT_DB_NAME", ""))
    base = EtlConfig.from_env(env)
    return EtlConfig(**{**base.__dict__, "retry": RetryPolicy(max_attempts=2, initial_delay_seconds=0)})


@pytest.fixture
def conn(config):
    conn = psycopg2.connect(**config.connection_kwargs())
    conn.autocommit = True
    with conn.cursor() as cur:
        cur.execute(f"DROP SCHEMA IF EXISTS {config.analytics_schema} CASCADE")
        cur.execute("DROP TABLE IF EXISTS positions, orders, instruments, accounts CASCADE")
        for folder in ("tables", "data"):
            for script in sorted((DB_DIR / folder).glob("*.sql")):
                cur.execute(script.read_text())
    yield conn
    conn.close()


def scalar(conn, sql):
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchone()[0]


def snapshot(conn, schema):
    with conn.cursor() as cur:
        cur.execute(f"""SELECT order_id, a.account_id, i.symbol, date_key, side, quantity,
                               signed_quantity, price, notional, status, is_filled
                        FROM {schema}.fact_trades f
                        JOIN {schema}.dim_account a USING (account_key)
                        JOIN {schema}.dim_instrument i USING (instrument_key)
                        ORDER BY order_id""")
        return cur.fetchall()


def test_pipeline_loads_all_source_orders(conn, config):
    result = EtlPipeline(config).run()

    assert result.succeeded, result.error
    schema = config.analytics_schema
    source_orders = scalar(conn, "SELECT COUNT(*) FROM orders")
    assert scalar(conn, f"SELECT COUNT(*) FROM {schema}.fact_trades") == source_orders
    assert scalar(conn, f"SELECT COUNT(*) FROM {schema}.dim_account") == scalar(conn, "SELECT COUNT(*) FROM accounts")
    assert result.load_stats.fact_trades == source_orders
    assert scalar(conn, f"SELECT status FROM {schema}.etl_run_log WHERE run_id = '{result.run_id}'") == "SUCCEEDED"


def test_rerun_is_repeatable_and_picks_up_source_changes(conn, config):
    schema = config.analytics_schema
    first = EtlPipeline(config).run()
    before = snapshot(conn, schema)

    second = EtlPipeline(config).run()

    assert second.succeeded
    assert snapshot(conn, schema) == before           # same data, same result
    assert second.load_stats.fact_trades == 0          # nothing rewritten
    assert first.load_stats.fact_trades == len(before)

    # An order moving NEW -> FILLED in the source is updated, not duplicated.
    with conn.cursor() as cur:
        cur.execute("UPDATE orders SET status = 'FILLED' WHERE idempotency_key = 'seed-key-5'")
    third = EtlPipeline(config).run()
    assert third.load_stats.fact_trades == 1
    assert scalar(conn, f"SELECT COUNT(*) FROM {schema}.fact_trades") == len(before)
    assert scalar(conn, f"SELECT is_filled FROM {schema}.fact_trades WHERE idempotency_key = 'seed-key-5'")


def test_failed_run_is_recorded_and_leaves_analytics_untouched(conn, config):
    schema = config.analytics_schema
    EtlPipeline(config).run()
    loaded = scalar(conn, f"SELECT COUNT(*) FROM {schema}.fact_trades")

    with conn.cursor() as cur:
        cur.execute("ALTER TABLE orders RENAME TO orders_offline")
    try:
        failed = EtlPipeline(config).run()
    finally:
        with conn.cursor() as cur:
            cur.execute("ALTER TABLE orders_offline RENAME TO orders")

    assert not failed.succeeded
    assert failed.failed_stage == "extract"
    assert scalar(conn, f"SELECT COUNT(*) FROM {schema}.fact_trades") == loaded
    with conn.cursor() as cur:
        cur.execute(f"SELECT status, failed_stage FROM {schema}.etl_run_log WHERE run_id = %s",
                    (failed.run_id,))
        assert cur.fetchone() == ("FAILED", "extract")
