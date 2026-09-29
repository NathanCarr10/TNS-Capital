"""End-to-end test against a real PostgreSQL database.

WARNING: this recreates the source tables from db/, so only run it against a
throwaway database:

    docker run -d --rm --name etl-it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
    ETL_IT=1 ETL_DB_NAME=postgres ETL_DB_PORT=55432 DB_USER=postgres DB_PASSWORD=it \
        python -m pytest tests/integration
    docker stop etl-it
"""

import os
from pathlib import Path

import psycopg2
import pytest

import config
import pipeline

pytestmark = pytest.mark.skipif(not os.getenv("ETL_IT"), reason="set ETL_IT=1 to run")

DB_FOLDER = Path(__file__).resolve().parents[3] / "db"


@pytest.fixture
def db():
    """A database containing only the seed data from db/."""
    conn = psycopg2.connect(**config.DB_SETTINGS)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("DROP SCHEMA IF EXISTS analytics CASCADE")
    cur.execute("DROP TABLE IF EXISTS positions, orders, instruments, accounts CASCADE")
    for folder in ["tables", "data"]:
        for script in sorted((DB_FOLDER / folder).glob("*.sql")):
            cur.execute(script.read_text())
    yield cur
    conn.close()


def query(cur, sql):
    cur.execute(sql)
    return cur.fetchall()


def test_all_orders_are_loaded(db):
    assert pipeline.run_pipeline() is True
    assert query(db, "SELECT COUNT(*) FROM analytics.fact_trades") == query(db, "SELECT COUNT(*) FROM orders")


def test_running_twice_gives_the_same_result(db):
    pipeline.run_pipeline()
    first = query(db, "SELECT * FROM analytics.fact_trades ORDER BY order_id")

    pipeline.run_pipeline()
    second = query(db, "SELECT * FROM analytics.fact_trades ORDER BY order_id")

    assert first == second  # no duplicates, no changes


def test_changed_orders_are_updated_not_duplicated(db):
    pipeline.run_pipeline()
    db.execute("UPDATE orders SET status = 'FILLED' WHERE idempotency_key = 'seed-key-5'")

    pipeline.run_pipeline()

    rows = query(db, """SELECT f.status FROM analytics.fact_trades f
                        JOIN orders o ON o.id = f.order_id
                        WHERE o.idempotency_key = 'seed-key-5'""")
    assert rows == [("FILLED",)]


def test_failed_run_leaves_reporting_tables_unchanged(db):
    pipeline.run_pipeline()
    before = query(db, "SELECT COUNT(*) FROM analytics.fact_trades")

    db.execute("ALTER TABLE orders RENAME TO orders_offline")  # make extract fail
    try:
        assert pipeline.run_pipeline() is False
    finally:
        db.execute("ALTER TABLE orders_offline RENAME TO orders")

    assert query(db, "SELECT COUNT(*) FROM analytics.fact_trades") == before
