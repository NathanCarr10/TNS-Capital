"""End-to-end test against a real PostgreSQL database.

It creates the app's tables from db/tables (so it checks the ETL still matches
the real schema) and fills them with the small test data set below.

WARNING: this drops and recreates tables, so only run it against a
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

TABLES_FOLDER = Path(__file__).resolve().parents[3] / "db" / "tables"

TEST_DATA = """
    INSERT INTO accounts (account_number, holder_name, cash_balance, status) VALUES
        ('ACC-1001', 'John Doe', 5000.00, 'ACTIVE'),
        ('ACC-1002', 'Jane Smith', 12000.00, 'ACTIVE');

    INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
        ('ACME', 'Acme Corp', 'EQUITY', 'USD', TRUE),
        ('BOND1', 'Gov Bond', 'BOND', 'EUR', TRUE);

    INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
        ('11111111-1111-1111-1111-111111111111', (SELECT id FROM accounts WHERE account_number = 'ACC-1001'),
            'ACME', 'BUY', 100, 25.00, 'FILLED', 'seed-key-1', '2026-08-01 09:00:00'),
        ('22222222-2222-2222-2222-222222222222', (SELECT id FROM accounts WHERE account_number = 'ACC-1002'),
            'BOND1', 'BUY', 50, 40.00, 'FILLED', 'seed-key-2', '2026-08-02 10:00:00'),
        ('55555555-5555-5555-5555-555555555555', (SELECT id FROM accounts WHERE account_number = 'ACC-1002'),
            'BOND1', 'SELL', 10, 41.00, 'NEW', 'seed-key-5', '2026-08-04 13:15:00');
"""


@pytest.fixture
def db():
    """A database with the app's tables and a small set of test data."""
    conn = psycopg2.connect(**config.DB_SETTINGS)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("DROP SCHEMA IF EXISTS analytics CASCADE")
    cur.execute("DROP TABLE IF EXISTS positions, orders, instruments, accounts CASCADE")
    for script in sorted(TABLES_FOLDER.glob("*.sql")):
        cur.execute(script.read_text())
    cur.execute(TEST_DATA)
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


def test_valid_seed_data_produces_no_rejects(db):
    assert pipeline.run_pipeline() is True
    assert query(db, "SELECT COUNT(*) FROM analytics.etl_rejected_orders") == [(0,)]
