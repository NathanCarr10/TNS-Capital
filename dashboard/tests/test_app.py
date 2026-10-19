"""The Streamlit page, rendered headlessly with AppTest and fake data (no database or auth service)."""

import time

import pandas as pd
import pytest

pytest.importorskip("streamlit")
jwt = pytest.importorskip("jwt")
from streamlit.testing.v1 import AppTest  # noqa: E402

import streamlit as st  # noqa: E402

import auth  # noqa: E402
import data  # noqa: E402
import monitoring  # noqa: E402

SECRET = "test-secret-of-at-least-32-bytes-long!!"

TRADES = pd.DataFrame([
    {"order_id": "o1", "account_number": "ACC-1", "holder_name": "Ann", "username": "ann", "symbol": "ACME", "asset_class": "Equity",
     "side": "BUY", "quantity": 10, "price": 5.0, "notional": 50.0, "status": "FILLED",
     "created_on": pd.Timestamp("2026-10-01 10:00"), "status_reason": None,
     "filled_on": pd.Timestamp("2026-10-01 10:00:02")},
    {"order_id": "o2", "account_number": "ACC-2", "holder_name": "Bob", "username": "bob", "symbol": "ACME", "asset_class": "Equity",
     "side": "SELL", "quantity": 2, "price": 6.0, "notional": 12.0, "status": "REJECTED",
     "created_on": pd.Timestamp("2026-10-02 10:00"), "status_reason": "Cannot sell 2 of ACME, only 0 available",
     "filled_on": None},
])
POSITIONS = pd.DataFrame([{"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 5.0}])


DLQ = pd.DataFrame([{"id": "d1", "order_id": "o2", "failure_type": "NON_RETRYABLE_InsufficientHoldingsException",
                     "failure_reason": "Cannot sell 2 of ACME", "status": "PENDING", "is_retryable": False,
                     "retry_count": 0, "created_on": pd.Timestamp("2026-10-02 10:00"), "resolved_on": None}])
FRAMES = {"live": TRADES, "batch": TRADES.iloc[:1], "positions": POSITIONS, "dlq": DLQ, "has_batch": True}


@pytest.fixture(autouse=True)
def fresh_cache(monkeypatch):
    # The page caches load() for 30 s; each test needs its own data
    st.cache_data.clear()
    monkeypatch.setenv("JWT_SECRET", SECRET)


def token(roles=("ADMIN",), sub="admin", expires_in=900):
    now = int(time.time())
    claims = {"sub": sub, "roles": list(roles), "typ": "access", "iss": auth.ISSUER, "iat": now,
              "exp": now + expires_in}
    return jwt.encode(claims, SECRET, algorithm="HS256")


def run_app(monkeypatch, frames, signed_in_with=None):
    """Render the page; signed in as an admin unless another token (or "" for none) is given."""
    monkeypatch.setattr(data, "load_all", lambda: frames)
    app = AppTest.from_file("../app.py", default_timeout=30)
    signed_in_with = token() if signed_in_with is None else signed_in_with
    if signed_in_with:
        app.session_state["token"] = signed_in_with
    return app.run()


def test_asks_for_sign_in_and_loads_no_data_without_a_token(monkeypatch):
    def must_not_load():
        raise AssertionError("loaded data before sign-in")

    monkeypatch.setattr(data, "load_all", must_not_load)
    app = AppTest.from_file("../app.py", default_timeout=30).run()

    assert not app.exception
    assert [field.label for field in app.text_input] == ["Username", "Password"]
    assert not app.metric


def test_admin_sign_in_opens_the_dashboard(monkeypatch):
    monkeypatch.setattr(auth, "login", lambda username, password: token())
    app = run_app(monkeypatch, FRAMES, signed_in_with="")

    app.text_input[0].input("admin")
    app.text_input[1].input("secret")
    app.button[0].click().run()

    assert not app.exception
    assert any(metric.label == "Orders" for metric in app.metric)
    assert "Signed in as **admin**" in app.sidebar.markdown[0].value


def test_wrong_password_shows_the_auth_message(monkeypatch):
    def rejected(username, password):
        raise auth.AuthError("Invalid username or password.")

    monkeypatch.setattr(auth, "login", rejected)
    app = run_app(monkeypatch, FRAMES, signed_in_with="")

    app.text_input[0].input("admin")
    app.text_input[1].input("wrong")
    app.button[0].click().run()

    assert app.error[0].value == "Invalid username or password."
    assert not app.metric


def test_a_trader_is_turned_away(monkeypatch):
    app = run_app(monkeypatch, FRAMES, signed_in_with=token(roles=["USER"], sub="ann"))

    assert "this dashboard is for admins only" in app.error[0].value
    assert not app.metric


def test_an_expired_session_goes_back_to_sign_in(monkeypatch):
    app = run_app(monkeypatch, FRAMES, signed_in_with=token(expires_in=-60))

    assert "session has expired" in app.warning[0].value
    assert "token" not in app.session_state
    assert not app.metric


def test_a_forged_token_is_rejected(monkeypatch):
    now = int(time.time())
    forged = jwt.encode({"sub": "mallory", "roles": ["ADMIN"], "typ": "access", "iss": auth.ISSUER,
                         "iat": now, "exp": now + 900}, "some-other-secret-of-32-bytes-long!!", algorithm="HS256")
    app = run_app(monkeypatch, FRAMES, signed_in_with=forged)

    assert "session is not valid" in app.warning[0].value
    assert not app.metric


def test_support_is_turned_away_and_pointed_to_the_trading_app(monkeypatch):
    app = run_app(monkeypatch, FRAMES, signed_in_with=token(roles=["SUPPORT"], sub="support"))

    assert "this dashboard is for admins only" in app.error[0].value
    assert "trading app" in app.info[0].value
    assert not app.metric


def test_has_no_link_to_the_trading_apps_account_viewer(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    assert not app.sidebar.get("link_button")  # traders' accounts are support's, not admins'


def test_sign_out_returns_to_the_form(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    app.sidebar.button[0].click().run()

    assert "token" not in app.session_state
    assert [field.label for field in app.text_input] == ["Username", "Password"]


def test_shows_what_each_user_traded_and_one_users_orders(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    [by_user] = [frame.value for frame in app.dataframe if "fill_rate" in frame.value]
    assert by_user["username"].tolist() == ["ann", "bob"]

    picker = app.selectbox[0]
    assert picker.label == "See one account holder's orders"
    assert picker.options == ["Ann (ACC-1)", "Bob (ACC-2)"]
    picker.select("ACC-2").run()

    assert not app.exception
    metrics = {metric.label: metric.value for metric in app.metric}
    assert metrics["Their orders"] == "1"
    assert metrics["Their fill rate"] == "0%"
    # The ETL-gap table also lists orders, but with their ids
    [theirs] = [frame.value for frame in app.dataframe if "notional" in frame.value and "order_id" not in frame.value]
    assert theirs["status"].tolist() == ["REJECTED"]



def test_accounts_without_a_login_are_picked_one_at_a_time(monkeypatch):
    # Seeded accounts have no login, so they all share the username "(no login)"
    no_login = TRADES.assign(username="(no login)")
    app = run_app(monkeypatch, {**FRAMES, "live": no_login})

    app.selectbox[0].select("ACC-1").run()

    assert not app.exception
    assert {metric.label: metric.value for metric in app.metric}["Their orders"] == "1"

def test_shows_metrics_insights_and_charts(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    assert not app.exception
    metrics = {metric.label: metric.value for metric in app.metric}
    assert metrics["Orders"] == "2"
    assert metrics["Fill rate"] == "50%"
    assert metrics["Orders not yet in batch"] == "1"
    assert "**Ann** (ACC-1) is the most active account" in app.markdown[0].value
    assert len(app.get("plotly_chart")) == 6


def test_switching_to_batch_warns_before_the_first_etl_run(monkeypatch):
    app = run_app(monkeypatch, {**FRAMES, "batch": TRADES.iloc[0:0], "has_batch": False})

    app.radio[0].set_value(app.radio[0].options[1]).run()

    assert not app.exception
    assert "ETL has not run yet" in app.warning[0].value


def test_reports_a_database_error(monkeypatch):
    def unreachable():
        raise OSError("connection refused")

    monkeypatch.setattr(data, "load_all", unreachable)
    app = AppTest.from_file("../app.py", default_timeout=30)
    app.session_state["token"] = token()
    app.run()

    assert "Could not reach the database" in app.error[0].value


def test_shows_why_orders_are_rejected(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    [rejected] = [frame.value for frame in app.dataframe if "category" in frame.value]
    assert rejected[["username", "category"]].values.tolist() == [["bob", "Insufficient holdings"]]


def test_shows_pipeline_health(monkeypatch):
    app = run_app(monkeypatch, FRAMES)

    metrics = {metric.label: metric.value for metric in app.metric}
    assert metrics["Median time to fill"] == "2.0 s"
    assert metrics["Stuck orders"] == "0"
    assert metrics["Dead letters awaiting review"] == "1"
    [dlq] = [frame.value for frame in app.dataframe if "failure_type" in frame.value]
    assert dlq["pending"].tolist() == [1]
    assert not app.warning


def test_warns_about_stuck_orders(monkeypatch):
    stuck = {**TRADES.iloc[0].to_dict(), "order_id": "o3", "status": "NEW", "filled_on": None,
             "created_on": pd.Timestamp("2026-01-01 09:00")}
    app = run_app(monkeypatch, {**FRAMES, "live": pd.DataFrame([*TRADES.to_dict("records"), stuck])})

    assert {metric.label: metric.value for metric in app.metric}["Stuck orders"] == "1"
    assert "1 order(s) have been waiting" in app.warning[0].value


def test_operations_sections_with_no_rejections_or_dead_letters(monkeypatch):
    app = run_app(monkeypatch, {**FRAMES, "live": TRADES.iloc[:1], "dlq": DLQ.iloc[0:0]})

    assert not app.exception
    assert "No orders have been rejected." in [info.value for info in app.info]
    assert {metric.label: metric.value for metric in app.metric}["Dead letters awaiting review"] == "0"


ALERTS = pd.DataFrame([{"name": "RefreshTokenReuse", "state": "firing", "severity": "critical", "owasp": "A09-1",
                        "since": pd.Timestamp("2026-10-19 10:00"), "summary": "Reused", "action": "Check the logs"}])


def fake_stack(monkeypatch, alerts=ALERTS):
    monkeypatch.setattr(monitoring, "configured", lambda: True)
    monkeypatch.setattr(monitoring, "alerts", lambda: alerts)
    monkeypatch.setattr(monitoring, "value", lambda query, default=None: 3.0)
    monkeypatch.setattr(monitoring, "instant", lambda query: pd.DataFrame(
        [{"state": "STREAMING", "value": 1.0}] if "stream" in query else [{"job": "api", "value": 1.0}]))
    monkeypatch.setattr(monitoring, "series", lambda query, minutes, label: pd.DataFrame(
        {"time": [pd.Timestamp("2026-10-19 10:00")], label: ["x"], "value": [1.0]}))
    monkeypatch.setattr(monitoring, "logs", lambda minutes, services, levels, text: pd.DataFrame(
        [{"time": pd.Timestamp("2026-10-19 10:00"), "service": "auth-service", "level": "warn",
          "message": f"matched {text!r}"}]))


def test_monitoring_says_how_to_start_the_stack_when_it_is_not_running(monkeypatch):
    monkeypatch.setattr(monitoring, "configured", lambda: False)
    app = run_app(monkeypatch, FRAMES)
    app.sidebar.get("button_group")[0].set_value("Monitoring").run()

    assert not app.exception
    assert "--profile observability" in app.info[0].value


def test_monitoring_shows_alerts_metrics_and_logs(monkeypatch):
    fake_stack(monkeypatch)
    app = run_app(monkeypatch, FRAMES)

    assert "1 alert(s) firing" in app.sidebar.error[0].value  # on the trading view too
    app.sidebar.get("button_group")[0].set_value("Monitoring").run()

    assert not app.exception
    assert app.title[0].value == "Monitoring"
    assert app.dataframe[0].value["name"].tolist() == ["RefreshTokenReuse"]
    labels = {metric.label: metric.value for metric in app.metric}
    assert labels["api"] == "✅ Up"
    assert labels["Alpaca price stream"] == "Streaming"
    assert labels["Failed logins"] == "3"
    assert labels["Slowest 5% fill in over"] == "3.0 s"
    assert not any(metric.label == "Orders" for metric in app.metric)  # no trading data loaded

    app.selectbox(key="log_preset").select("Failed logins (A09-2)").run()
    assert app.dataframe[-1].value["message"].tolist() == ["matched 'Login failed'"]


def test_monitoring_reports_a_stack_that_does_not_answer(monkeypatch):
    fake_stack(monkeypatch)

    def down():
        raise monitoring.Unavailable("http://vmalert:8880 did not answer. Is the observability profile running?")

    monkeypatch.setattr(monitoring, "alerts", down)
    app = run_app(monkeypatch, FRAMES)
    app.sidebar.get("button_group")[0].set_value("Monitoring").run()

    assert not app.exception
    assert "did not answer" in app.error[0].value
