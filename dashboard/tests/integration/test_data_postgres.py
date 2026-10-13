"""data.py against a real PostgreSQL database.

It creates the app's tables from db/tables, adds a little data, and checks
both sources load. WARNING: this drops and recreates tables, so only run it
against a throwaway database:

    docker run -d --rm --name dash-it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
    DASHBOARD_IT=1 DASHBOARD_DB_NAME=postgres DASHBOARD_DB_PORT=55432 DB_USER=postgres DB_PASSWORD=it \\
        python -m pytest tests/integration
    docker stop dash-it
"""

import os
from pathlib import Path

import psycopg2
import pytest

import data
import insights

pytestmark = pytest.mark.skipif(not os.getenv("DASHBOARD_IT"), reason="set DASHBOARD_IT=1 to run")

REPO = Path(__file__).resolve().parents[3]

TEST_DATA = """
    INSERT INTO accounts (account_number, holder_name, cash_balance, status) VALUES
        ('ACC-1001', 'John Doe', 5000.00, 'ACTIVE');
    INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
        ('ACME', 'Acme Corp', 'Equity', 'USD', TRUE);
    INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key) VALUES
        ('11111111-1111-1111-1111-111111111111', (SELECT id FROM accounts), 'ACME', 'BUY', 10, 25.00, 'FILLED', 'k1'),
        ('22222222-2222-2222-2222-222222222222', (SELECT id FROM accounts), 'ACME', 'BUY', 5, 26.00, 'REJECTED', 'k2');
    INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
        ((SELECT id FROM accounts), 'ACME', 10, 25.00);
    UPDATE orders SET status_reason = 'Insufficient funds for buy order'
        WHERE id = '22222222-2222-2222-2222-222222222222';
    INSERT INTO executions (id, order_id, quantity, price, venue, executed_on)
        SELECT gen_random_uuid(), id, 10, 25.00, 'SIM', created_on + INTERVAL '2 seconds'
        FROM orders WHERE id = '11111111-1111-1111-1111-111111111111';
    INSERT INTO dlq_messages (original_order_id, original_message, failure_reason, failure_type, is_retryable)
        VALUES ('22222222-2222-2222-2222-222222222222', '{}', 'Insufficient funds for buy order',
                'NON_RETRYABLE_InsufficientFundsException', FALSE);
    INSERT INTO users (username, password_hash, role, account_id) VALUES
        ('john', 'not-a-real-hash', 'USER', (SELECT id FROM accounts));
"""


@pytest.fixture
def db():
    conn = psycopg2.connect(**data.DB_SETTINGS)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("DROP SCHEMA IF EXISTS analytics CASCADE")
    cur.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
    for script in sorted((REPO / "db" / "tables").glob("*.sql")):
        cur.execute(script.read_text())
    cur.execute(TEST_DATA)
    yield cur
    conn.close()


def test_live_source_loads_before_the_etl_has_run(db):
    frames = data.load_all()

    assert frames["has_batch"] is False
    assert frames["batch"].empty
    assert sorted(frames["live"]["status"]) == ["FILLED", "REJECTED"]
    assert set(frames["live"]["username"]) == {"john"}
    assert insights.rejection_reasons(frames["live"])["category"].tolist() == ["Insufficient funds"]
    assert insights.fill_time_summary(frames["live"])["median"] == pytest.approx(2.0)
    assert insights.dlq_summary(frames["dlq"]).to_dict("records") == [
        {"failure_type": "NON_RETRYABLE_InsufficientFundsException", "messages": 1, "pending": 1, "retryable": 0}]
    assert frames["live"]["notional"].sum() == pytest.approx(380.0)  # 10 x 25 + 5 x 26
    assert insights.fill_rate(frames["live"]) == pytest.approx(0.5)
    assert frames["positions"].to_dict("records") == [
        {"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 25.0}]


def test_batch_source_loads_once_the_etl_has_run(db):
    db.execute((REPO / "etl" / "schema.sql").read_text())
    db.execute("""
        INSERT INTO analytics.dim_account VALUES ('ACC-1001', 'John Doe', 'ACTIVE');
        INSERT INTO analytics.dim_instrument VALUES ('ACME', 'Acme Corp', 'Equity', 'USD');
        INSERT INTO analytics.dim_date VALUES (20261012, '2026-10-12', 2026, 4, 10, 'October', 12, 'Monday', FALSE);
        INSERT INTO analytics.fact_trades VALUES ('11111111-1111-1111-1111-111111111111', 'ACC-1001', 'ACME',
            20261012, 'BUY', 10, 10, 25.00, 250.00, 'FILLED', TRUE, '2026-10-12 09:00:00');
    """)

    frames = data.load_all()

    assert frames["has_batch"] is True
    assert frames["batch"]["order_id"].tolist() == ["11111111-1111-1111-1111-111111111111"]
    assert frames["batch"]["username"].tolist() == ["john"]
    assert insights.rejection_reasons(frames["batch"]).empty  # reasons are only in the live source
    assert insights.not_yet_in_batch(frames["live"], frames["batch"])["order_id"].tolist() == [
        "22222222-2222-2222-2222-222222222222"]


def test_accounts_without_a_login_are_labelled(db):
    db.execute("DELETE FROM users")

    frames = data.load_all()

    assert set(frames["live"]["username"]) == {"(no login)"}
