"""The Streamlit page, rendered headlessly with AppTest and fake data (no database or auth service)."""

import time
from datetime import timedelta

import pandas as pd
import pytest

pytest.importorskip("streamlit")
jwt = pytest.importorskip("jwt")
from streamlit.testing.v1 import AppTest  # noqa: E402

import streamlit as st  # noqa: E402

import auth  # noqa: E402
import data  # noqa: E402
import monitoring  # noqa: E402
import sessions  # noqa: E402

SECRET = "test-secret-of-at-least-32-bytes-long!!"

# Placed in the last few hours, so the Live view's default period (24 hours) includes them
NOW = pd.Timestamp.now(tz="UTC").tz_localize(None).floor("s")
TRADES = pd.DataFrame([
    {"order_id": "o1", "account_number": "ACC-1", "holder_name": "Ann", "email": "ann@example.com", "symbol": "ACME", "asset_class": "Equity",
     "side": "BUY", "quantity": 10, "price": 5.0, "notional": 50.0, "status": "FILLED",
     "created_on": NOW - timedelta(hours=3), "status_reason": None,
     "filled_on": NOW - timedelta(hours=3) + timedelta(seconds=2), "strategy_name": None},
    {"order_id": "o2", "account_number": "ACC-2", "holder_name": "Bob", "email": "bob@example.com", "symbol": "ACME", "asset_class": "Equity",
     "side": "SELL", "quantity": 2, "price": 6.0, "notional": 12.0, "status": "REJECTED",
     "created_on": NOW - timedelta(hours=2), "status_reason": "Cannot sell 2 of ACME, only 0 available",
     "filled_on": None, "strategy_name": None},
])
POSITIONS = pd.DataFrame([{"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 5.0}])


DLQ = pd.DataFrame([{"id": "d1", "order_id": "o2", "failure_type": "NON_RETRYABLE_InsufficientHoldingsException",
                     "failure_reason": "Cannot sell 2 of ACME", "status": "PENDING", "is_retryable": False,
                     "retry_count": 0, "created_on": pd.Timestamp("2026-10-02 10:00"), "resolved_on": None}])
PRICES = pd.DataFrame([{"symbol": "ACME", "trade_date": (NOW - timedelta(days=d)).date(), "close": 5.0 + d}
                       for d in range(3)])
ETL_REJECTS = pd.DataFrame([{"order_id": "o9", "account_number": "ACC-1", "symbol": "NOPE", "side": "BUY",
                             "quantity": "1", "price": "1.00", "status": "NEW", "created_on": "2026-10-01",
                             "reject_reason": "unknown symbol", "rejected_at": NOW}])
LIVE = {"trades": TRADES, "positions": POSITIONS, "dlq": DLQ}
# The ETL has loaded o1 only, so o2 is waiting for its next run
BATCH = {"available": True, "trades": TRADES.iloc[:1], "prices": PRICES, "etl_rejects": ETL_REJECTS}
NO_BATCH = {"available": False, "trades": pd.DataFrame(), "prices": pd.DataFrame(), "etl_rejects": pd.DataFrame()}


@pytest.fixture(autouse=True)
def fresh_cache(monkeypatch):
    # The page caches load() for 30 s; each test needs its own data
    st.cache_data.clear()
    monkeypatch.setenv("JWT_SECRET", SECRET)
    # A fresh session store, and a browser with no session cookie unless a test sends one
    monkeypatch.setattr(sessions, "STORE", sessions.SessionStore())
    monkeypatch.setattr(sessions, "browser_cookie", lambda: None)


def token(roles=("ADMIN",), sub="admin@tns-capital.local", expires_in=900):
    now = int(time.time())
    claims = {"sub": sub, "roles": list(roles), "typ": "access", "iss": auth.ISSUER, "iat": now,
              "exp": now + expires_in}
    return jwt.encode(claims, SECRET, algorithm="HS256")


def run_app(monkeypatch, live=LIVE, batch=BATCH, signed_in_with=None, view=None):
    """Render the page; signed in as an admin unless another token (or "" for none) is given.

    It opens on the Live view; view switches to another one in the sidebar.
    """
    monkeypatch.setattr(data, "load_live", lambda: live)
    monkeypatch.setattr(data, "load_batch", lambda: batch)
    app = AppTest.from_file("../app.py", default_timeout=30)
    signed_in_with = token() if signed_in_with is None else signed_in_with
    if signed_in_with:
        app.session_state["token"] = signed_in_with
    app.run()
    if view:
        app.sidebar.get("button_group")[0].set_value(view).run()
    return app


def metrics(app):
    return {metric.label: metric.value for metric in app.metric}


def account_picker(app):
    [picker] = [box for box in app.selectbox if box.label == "See one account holder's orders"]
    return picker


def test_asks_for_sign_in_and_loads_no_data_without_a_token(monkeypatch):
    def must_not_load():
        raise AssertionError("loaded data before sign-in")

    monkeypatch.setattr(data, "load_live", must_not_load)
    monkeypatch.setattr(data, "load_batch", must_not_load)
    app = AppTest.from_file("../app.py", default_timeout=30).run()

    assert not app.exception
    assert [field.label for field in app.text_input] == ["Email", "Password"]
    assert not app.metric


def test_admin_sign_in_opens_the_dashboard(monkeypatch):
    monkeypatch.setattr(auth, "login", lambda email, password: (token(), "refresh-1"))
    app = run_app(monkeypatch, signed_in_with="")

    app.text_input[0].input("admin")
    app.text_input[1].input("secret")
    app.button[0].click().run()

    assert not app.exception
    assert any(metric.label == "Orders" for metric in app.metric)
    assert "Signed in as **admin@tns-capital.local**" in app.sidebar.markdown[0].value


def test_wrong_password_shows_the_auth_message(monkeypatch):
    def rejected(email, password):
        raise auth.AuthError("Invalid email or password.")

    monkeypatch.setattr(auth, "login", rejected)
    app = run_app(monkeypatch, signed_in_with="")

    app.text_input[0].input("admin")
    app.text_input[1].input("wrong")
    app.button[0].click().run()

    assert app.error[0].value == "Invalid email or password."
    assert not app.metric


def test_a_trader_is_turned_away(monkeypatch):
    app = run_app(monkeypatch, signed_in_with=token(roles=["USER"], sub="ann@example.com"))

    assert "this dashboard is for admins only" in app.error[0].value
    assert not app.metric


def test_an_expired_session_goes_back_to_sign_in(monkeypatch):
    app = run_app(monkeypatch, signed_in_with=token(expires_in=-60))

    assert "session has expired" in app.warning[0].value
    assert "token" not in app.session_state
    assert not app.metric


def test_a_forged_token_is_rejected(monkeypatch):
    now = int(time.time())
    forged = jwt.encode({"sub": "mallory", "roles": ["ADMIN"], "typ": "access", "iss": auth.ISSUER,
                         "iat": now, "exp": now + 900}, "some-other-secret-of-32-bytes-long!!", algorithm="HS256")
    app = run_app(monkeypatch, signed_in_with=forged)

    assert "session is not valid" in app.warning[0].value
    assert not app.metric


def test_support_is_turned_away_and_pointed_to_the_trading_app(monkeypatch):
    app = run_app(monkeypatch, signed_in_with=token(roles=["SUPPORT"], sub="support@tns-capital.local"))

    assert "this dashboard is for admins only" in app.error[0].value
    assert "trading app" in app.info[0].value
    assert not app.metric


def test_has_no_link_to_the_trading_apps_account_viewer(monkeypatch):
    app = run_app(monkeypatch)

    assert not app.sidebar.get("link_button")  # traders' accounts are support's, not admins'


def test_sign_out_returns_to_the_form(monkeypatch):
    app = run_app(monkeypatch)

    app.sidebar.button[0].click().run()

    assert "token" not in app.session_state
    assert [field.label for field in app.text_input] == ["Email", "Password"]


def test_live_view_shows_the_period_and_what_needs_attention(monkeypatch):
    app = run_app(monkeypatch)

    assert not app.exception
    assert app.title[0].value == "Live trading"
    shown = metrics(app)
    assert shown["Orders"] == "2"
    assert shown["Fill rate"] == "50%"
    assert shown["Filled value"] == "$50.00"
    assert shown["Rejected"] == "1"
    assert shown["Stuck orders"] == "0"
    assert shown["Dead letters awaiting review"] == "1"
    assert shown["Median time to fill"] == "2.0 s"
    [dlq] = [frame.value for frame in app.dataframe if "failure_type" in frame.value]
    assert dlq["pending"].tolist() == [1]
    [rejected] = [frame.value for frame in app.dataframe if "category" in frame.value]
    assert rejected[["email", "category"]].values.tolist() == [["bob@example.com", "Insufficient holdings"]]
    [latest] = [frame.value for frame in app.dataframe if "strategy_name" in frame.value]
    assert latest["status"].tolist() == ["REJECTED", "FILLED"]  # newest first
    # Rejections, most active accounts, exposure
    assert len(app.get("plotly_chart")) == 3
    assert not app.warning


def test_live_view_leaves_out_the_batch_reports(monkeypatch):
    app = run_app(monkeypatch)

    assert "Orders not yet loaded" not in metrics(app)
    assert not any("fill_rate" in frame.value for frame in app.dataframe)  # the per-user table
    assert not any("most active account" in markdown.value for markdown in app.markdown)


def test_live_view_counts_only_orders_in_the_period(monkeypatch):
    app = run_app(monkeypatch)

    app.selectbox(key="live_period").select("Last hour").run()

    assert not app.exception
    shown = metrics(app)
    assert shown["Orders"] == "0"
    assert shown["Dead letters awaiting review"] == "1"  # the state now, whatever the period
    assert "No orders were rejected in this period." in [caption.value for caption in app.caption]


def test_warns_about_stuck_orders(monkeypatch):
    stuck = {**TRADES.iloc[0].to_dict(), "order_id": "o3", "status": "NEW", "filled_on": None,
             "created_on": pd.Timestamp("2026-01-01 09:00")}
    app = run_app(monkeypatch, live={**LIVE, "trades": pd.DataFrame([*TRADES.to_dict("records"), stuck])})

    assert metrics(app)["Stuck orders"] == "1"  # whatever the period: it was placed months ago
    assert "1 order(s) have been waiting" in app.warning[0].value


def test_says_when_nothing_needs_attention(monkeypatch):
    app = run_app(monkeypatch, live={**LIVE, "trades": TRADES.iloc[:1], "dlq": DLQ.iloc[0:0]})

    assert not app.exception
    assert "Nothing is waiting" in app.success[0].value
    assert metrics(app)["Dead letters awaiting review"] == "0"


def test_live_view_lists_strategies_trading_in_the_period(monkeypatch):
    live = TRADES.assign(strategy_name=["MA 20/50", None])
    app = run_app(monkeypatch, live={**LIVE, "trades": live})

    [strategies] = [frame.value for frame in app.dataframe if "last_order" in frame.value]
    assert strategies["strategy_name"].tolist() == ["MA 20/50"]


def test_reports_a_database_error(monkeypatch):
    def unreachable():
        raise OSError("connection refused")

    monkeypatch.setattr(data, "load_live", unreachable)
    app = AppTest.from_file("../app.py", default_timeout=30)
    app.session_state["token"] = token()
    app.run()

    assert "Could not reach the database" in app.error[0].value


def test_batch_view_shows_history_insights_and_data_quality(monkeypatch):
    app = run_app(monkeypatch, view="Batch")

    assert not app.exception
    assert app.title[0].value == "Trading history"
    shown = metrics(app)
    assert shown["Orders"] == "1"
    assert shown["Fill rate"] == "100%"
    assert shown["Orders not yet loaded"] == "1"
    assert shown["Orders the latest ETL run refused"] == "1"
    assert "1 newer order(s) load on its next run" in app.caption[0].value
    assert "**Ann** (ACC-1) is the most active account" in app.markdown[0].value
    assert "**ACME** (Equity) is the most traded instrument" in app.markdown[0].value
    [reasons] = [frame.value for frame in app.dataframe if "reject_reason" in frame.value and "orders" in frame.value]
    assert reasons.to_dict("records") == [{"reject_reason": "unknown symbol", "orders": 1}]
    # Value and fill rate trends, asset classes, top instruments, fills against the market
    assert len(app.get("plotly_chart")) == 5


def test_a_reload_stays_on_the_chosen_view(monkeypatch):
    app = run_app(monkeypatch, view="Batch")
    assert app.query_params["view"] == "Batch"

    monkeypatch.setattr(data, "load_batch", lambda: BATCH)
    reloaded = AppTest.from_file("../app.py", default_timeout=30)
    reloaded.session_state["token"] = token()
    reloaded.query_params["view"] = "Batch"  # what the browser sends back after a reload
    reloaded.run()

    assert not reloaded.exception
    assert reloaded.title[0].value == "Trading history"


def test_an_unknown_view_in_the_url_opens_live(monkeypatch):
    monkeypatch.setattr(data, "load_live", lambda: LIVE)
    app = AppTest.from_file("../app.py", default_timeout=30)
    app.session_state["token"] = token()
    app.query_params["view"] = "Nope"
    app.run()

    assert not app.exception
    assert app.title[0].value == "Live trading"


def test_batch_view_leaves_out_what_only_live_has(monkeypatch):
    app = run_app(monkeypatch, view="Batch")

    for live_only in ["Stuck orders", "Dead letters awaiting review", "Median time to fill", "Rejected"]:
        assert live_only not in metrics(app)
    assert not any("category" in frame.value for frame in app.dataframe)


def test_batch_trends_group_by_week_or_month(monkeypatch):
    app = run_app(monkeypatch, view="Batch")

    app.get("button_group")[0].set_value("Month").run()

    assert not app.exception
    assert len(app.get("plotly_chart")) == 5


def test_batch_view_says_when_no_prices_are_loaded(monkeypatch):
    app = run_app(monkeypatch, batch={**BATCH, "prices": pd.DataFrame()}, view="Batch")

    assert not app.exception
    assert any("No daily prices loaded yet" in caption.value for caption in app.caption)


def test_batch_view_warns_before_the_first_etl_run(monkeypatch):
    app = run_app(monkeypatch, batch=NO_BATCH, view="Batch")

    assert not app.exception
    assert "ETL has not run yet" in app.warning[0].value
    assert not app.metric


def test_shows_what_each_user_traded_and_one_users_orders(monkeypatch):
    app = run_app(monkeypatch, batch={**BATCH, "trades": TRADES}, view="Batch")

    [by_user] = [frame.value for frame in app.dataframe if "fill_rate" in frame.value and "email" in frame.value]
    assert by_user["email"].tolist() == ["ann@example.com", "bob@example.com"]

    picker = account_picker(app)
    assert picker.options == ["Ann (ACC-1)", "Bob (ACC-2)"]
    picker.select("ACC-2").run()

    assert not app.exception
    shown = metrics(app)
    assert shown["Their orders"] == "1"
    assert shown["Their fill rate"] == "0%"
    # The ETL-gap table also lists orders, but with their ids
    [theirs] = [frame.value for frame in app.dataframe if "notional" in frame.value and "order_id" not in frame.value]
    assert theirs["status"].tolist() == ["REJECTED"]


def test_accounts_without_a_login_are_picked_one_at_a_time(monkeypatch):
    # Seeded accounts have no login, so they all share the email "(no login)"
    no_login = TRADES.assign(email="(no login)")
    app = run_app(monkeypatch, batch={**BATCH, "trades": no_login}, view="Batch")

    account_picker(app).select("ACC-1").run()

    assert not app.exception
    assert metrics(app)["Their orders"] == "1"


def test_shows_strategy_orders_next_to_the_traders_own(monkeypatch):
    trades = TRADES.assign(strategy_name=["MA 20/50", None])
    app = run_app(monkeypatch, batch={**BATCH, "trades": trades}, view="Batch")

    assert not app.exception
    shown = metrics(app)
    assert shown["Orders placed by strategies"] == "1"
    assert shown["Strategies that traded"] == "1"
    assert "Automated strategies placed **50%** of all orders." in app.markdown[0].value


def test_says_when_no_strategy_has_traded(monkeypatch):
    app = run_app(monkeypatch, view="Batch")

    assert any("No strategy has placed an order yet" in info.value for info in app.info)


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
    app = run_app(monkeypatch)
    app.sidebar.get("button_group")[0].set_value("Monitoring").run()

    assert not app.exception
    assert "--profile observability" in app.info[0].value


def test_monitoring_shows_alerts_metrics_and_logs(monkeypatch):
    fake_stack(monkeypatch)
    app = run_app(monkeypatch)

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
    app = run_app(monkeypatch)
    app.sidebar.get("button_group")[0].set_value("Monitoring").run()

    assert not app.exception
    assert "did not answer" in app.error[0].value


def cookie_scripts(app):
    # The other html elements are theme.py's styles
    return [element.proto.body for element in app.get("html") if "<script>" in element.proto.body]


def test_sign_in_keeps_the_tokens_on_the_server_and_only_a_session_id_in_the_cookie(monkeypatch):
    monkeypatch.setattr(auth, "login", lambda email, password: (token(), "refresh-1"))
    app = run_app(monkeypatch, signed_in_with="")

    app.text_input[0].input("admin")
    app.text_input[1].input("secret")
    app.button[0].click().run()

    session_id = app.session_state["session_id"]
    assert sessions.STORE.access_token(session_id) == app.session_state["token"]
    [script] = cookie_scripts(app)
    assert f"{sessions.COOKIE}={session_id}; Path=/analytics" in script
    assert "refresh-1" not in script


def test_a_reload_resumes_the_session_from_the_cookie(monkeypatch):
    session_id = sessions.STORE.create(token(), "refresh-1")
    monkeypatch.setattr(sessions, "browser_cookie", lambda: session_id)

    app = run_app(monkeypatch, signed_in_with="")  # a new connection: empty session state

    assert not app.exception
    assert any(metric.label == "Orders" for metric in app.metric)
    assert app.session_state["session_id"] == session_id


def test_an_unknown_or_ended_session_cookie_shows_the_sign_in_form(monkeypatch):
    monkeypatch.setattr(sessions, "browser_cookie", lambda: "made-up")

    app = run_app(monkeypatch, signed_in_with="")

    assert [field.label for field in app.text_input] == ["Email", "Password"]


def test_an_expired_access_token_is_refreshed_without_signing_out(monkeypatch):
    expired = token(expires_in=-60)
    session_id = sessions.STORE.create(expired, "refresh-1")
    monkeypatch.setattr(sessions, "browser_cookie", lambda: session_id)
    refreshed = token()
    calls = []

    def refresh(refresh_token):
        calls.append(refresh_token)
        return refreshed, "refresh-2"

    monkeypatch.setattr(auth, "refresh", refresh)
    app = run_app(monkeypatch, signed_in_with="")

    assert calls == ["refresh-1"]
    assert app.session_state["token"] == refreshed
    assert any(metric.label == "Orders" for metric in app.metric)


def test_a_refused_refresh_signs_out(monkeypatch):
    session_id = sessions.STORE.create(token(expires_in=-60), "spent")
    monkeypatch.setattr(sessions, "browser_cookie", lambda: session_id)

    def refused(refresh_token):
        raise auth.AuthError("Your session has expired. Please sign in again.")

    monkeypatch.setattr(auth, "refresh", refused)
    app = run_app(monkeypatch, signed_in_with="")

    assert "expired" in app.warning[0].value
    assert sessions.STORE.access_token(session_id) is None


def test_sign_out_ends_the_server_session_and_clears_the_cookie(monkeypatch):
    session_id = sessions.STORE.create(token(), "refresh-1")
    monkeypatch.setattr(sessions, "browser_cookie", lambda: session_id)
    revoked = []
    monkeypatch.setattr(auth, "logout", revoked.append)
    app = run_app(monkeypatch, signed_in_with="")

    app.sidebar.button[0].click().run()

    assert revoked == ["refresh-1"]
    assert sessions.STORE.access_token(session_id) is None
    assert any("Max-Age=0" in script for script in cookie_scripts(app))
    assert [field.label for field in app.text_input] == ["Email", "Password"]
