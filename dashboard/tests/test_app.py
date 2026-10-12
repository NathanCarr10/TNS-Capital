"""The Streamlit page, rendered headlessly with AppTest and fake data (no database)."""

import pandas as pd
import pytest

pytest.importorskip("streamlit")
from streamlit.testing.v1 import AppTest  # noqa: E402

import streamlit as st  # noqa: E402

import data  # noqa: E402

TRADES = pd.DataFrame([
    {"order_id": "o1", "account_number": "ACC-1", "holder_name": "Ann", "symbol": "ACME", "asset_class": "Equity",
     "side": "BUY", "quantity": 10, "price": 5.0, "notional": 50.0, "status": "FILLED",
     "created_on": pd.Timestamp("2026-10-01 10:00")},
    {"order_id": "o2", "account_number": "ACC-2", "holder_name": "Bob", "symbol": "ACME", "asset_class": "Equity",
     "side": "SELL", "quantity": 2, "price": 6.0, "notional": 12.0, "status": "REJECTED",
     "created_on": pd.Timestamp("2026-10-02 10:00")},
])
POSITIONS = pd.DataFrame([{"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 5.0}])


@pytest.fixture(autouse=True)
def fresh_cache():
    # The page caches load() for 30 s; each test needs its own data
    st.cache_data.clear()


def run_app(monkeypatch, frames):
    monkeypatch.setattr(data, "load_all", lambda: frames)
    return AppTest.from_file("../app.py", default_timeout=30).run()


def test_shows_metrics_insights_and_charts(monkeypatch):
    app = run_app(monkeypatch, {"live": TRADES, "batch": TRADES.iloc[:1], "positions": POSITIONS, "has_batch": True})

    assert not app.exception
    metrics = {metric.label: metric.value for metric in app.metric}
    assert metrics["Orders"] == "2"
    assert metrics["Fill rate"] == "50%"
    assert metrics["Orders not yet in batch"] == "1"
    assert "**Ann** (ACC-1) is the most active account" in app.markdown[0].value
    assert len(app.get("plotly_chart")) == 5


def test_switching_to_batch_warns_before_the_first_etl_run(monkeypatch):
    app = run_app(monkeypatch, {"live": TRADES, "batch": TRADES.iloc[0:0], "positions": POSITIONS, "has_batch": False})

    app.radio[0].set_value(app.radio[0].options[1]).run()

    assert not app.exception
    assert "ETL has not run yet" in app.warning[0].value


def test_reports_a_database_error(monkeypatch):
    def unreachable():
        raise OSError("connection refused")

    monkeypatch.setattr(data, "load_all", unreachable)
    app = AppTest.from_file("../app.py", default_timeout=30).run()

    assert "Could not reach the database" in app.error[0].value
