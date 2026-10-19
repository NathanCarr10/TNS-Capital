"""The monitoring stack's answers turned into DataFrames, and the LogsQL the log search sends."""

import json

import pandas as pd
import pytest

requests = pytest.importorskip("requests")

import monitoring  # noqa: E402


class FakeResponse:
    def __init__(self, body, status=200):
        self.body = body
        self.status_code = status
        self.text = body if isinstance(body, str) else json.dumps(body)

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"HTTP {self.status_code}")

    def json(self):
        return self.body


class Calls(list):
    """The (url, params) of each request, plus answer_with(body) to set the fake stack's reply."""


@pytest.fixture
def stack(monkeypatch):
    """Points the module at a fake stack; returns the requests it receives."""
    monkeypatch.setattr(monitoring, "METRICS_URL", "http://vm:8428")
    monkeypatch.setattr(monitoring, "ALERTS_URL", "http://vmalert:8880")
    monkeypatch.setattr(monitoring, "LOGS_URL", "http://vlogs:9428")
    calls = Calls()

    def answer_with(body, status=200):
        def get(url, params=None, timeout=None):
            calls.append((url, params))
            return FakeResponse(body, status)
        monkeypatch.setattr(monitoring.requests, "get", get)
    calls.answer_with = answer_with
    return calls


def test_is_not_configured_without_the_urls(monkeypatch):
    monkeypatch.setattr(monitoring, "METRICS_URL", None)
    assert not monitoring.configured()
    with pytest.raises(monitoring.Unavailable, match="not configured"):
        monitoring.instant("up")


def test_instant_query_gives_labels_and_values(stack):
    stack.answer_with({"status": "success", "data": {"result": [
        {"metric": {"job": "api"}, "value": [1760000000, "1"]},
        {"metric": {"job": "auth"}, "value": [1760000000, "0"]},
    ]}})

    frame = monitoring.instant("up")

    assert stack[0] == ("http://vm:8428/api/v1/query", {"query": "up"})
    assert frame.to_dict("records") == [{"job": "api", "value": 1.0}, {"job": "auth", "value": 0.0}]


def test_value_falls_back_to_the_default_without_data(stack):
    stack.answer_with({"status": "success", "data": {"result": []}})
    assert monitoring.value("trading_dlq_pending") is None
    assert monitoring.value("trading_dlq_pending", default=0) == 0


def test_a_failed_query_is_unavailable(stack):
    stack.answer_with({"status": "error", "error": "bad query"})
    with pytest.raises(monitoring.Unavailable, match="bad query"):
        monitoring.instant("up(")


def test_an_unreachable_stack_is_unavailable(stack, monkeypatch):
    def refused(url, params=None, timeout=None):
        raise requests.ConnectionError("refused")

    monkeypatch.setattr(monitoring.requests, "get", refused)
    with pytest.raises(monitoring.Unavailable, match="observability profile"):
        monitoring.instant("up")


def test_range_query_gives_one_row_per_point_and_drops_gaps(stack):
    stack.answer_with({"status": "success", "data": {"result": [
        {"metric": {"side": "BUY"}, "values": [[1760000000, "2"], [1760000060, "NaN"]]},
        {"metric": {"side": "SELL"}, "values": [[1760000000, "1"]]},
    ]}})

    frame = monitoring.series(monitoring.ORDERS_ACCEPTED, 60, "side")

    assert stack[0][1]["step"] == 30
    assert frame[["side", "value"]].to_dict("records") == [{"side": "BUY", "value": 2.0},
                                                           {"side": "SELL", "value": 1.0}]
    assert frame["time"].iloc[0] == pd.Timestamp("2025-10-09 08:53:20")


def test_steps_give_about_120_points_but_never_under_the_scrape_interval():
    assert monitoring.step_seconds(60) == 30
    assert monitoring.step_seconds(10080) == 5040
    assert monitoring.step_seconds(15) == 10


def test_alerts_come_most_severe_first(stack):
    stack.answer_with({"status": "success", "data": {"alerts": [
        {"name": "DeadLettersPending", "state": "firing", "activeAt": "2026-10-19T10:00:00Z",
         "labels": {"severity": "warning", "owasp": "A09-4"},
         "annotations": {"summary": "1 failed order message(s) are waiting", "action": "Review them"}},
        {"name": "RefreshTokenReuse", "state": "firing", "activeAt": "2026-10-19T11:00:00Z",
         "labels": {"severity": "critical", "owasp": "A09-1"}, "annotations": {"summary": "Reused"}},
    ]}})

    frame = monitoring.alerts()

    assert stack[0][0] == "http://vmalert:8880/api/v1/alerts"
    assert list(frame["name"]) == ["RefreshTokenReuse", "DeadLettersPending"]
    assert frame.iloc[1]["owasp"] == "A09-4"
    assert frame.iloc[1]["since"] == pd.Timestamp("2026-10-19 10:00:00")
    assert frame.iloc[0]["action"] == ""


def test_no_alerts_is_an_empty_table():
    assert monitoring.parse_alerts([]).empty
    assert list(monitoring.parse_alerts([]).columns) == monitoring.ALERT_COLUMNS


def test_logsql_filters_by_time_only_by_default():
    assert monitoring.logsql(60) == ("_time:60m | sort by (_time desc) | limit 200 "
                                     "| fields _time, service, level, _msg")


def test_logsql_quotes_the_text_so_it_cannot_add_filters_or_pipes():
    query = monitoring.logsql(15, services=["app", "auth-service"], levels=["error"],
                              text='x" | delete | "y')

    assert query.startswith('_time:15m {service in("app","auth-service")} level:in("error") "x\\" | delete | \\"y" |')


def test_logs_become_a_table(stack):
    stack.answer_with("\n".join([
        json.dumps({"_time": "2026-10-19T13:17:20.789Z", "service": "auth-service", "level": "warn",
                    "_msg": 'Login failed: username="bob" ip=1.2.3.4'}),
        "",
    ]))

    frame = monitoring.logs(60, text="Login failed")

    url, params = stack[0]
    assert url == "http://vlogs:9428/select/logsql/query"
    assert '"Login failed"' in params["query"]
    assert frame.to_dict("records") == [{"time": pd.Timestamp("2026-10-19 13:17:20.789"), "service": "auth-service",
                                         "level": "warn", "message": 'Login failed: username="bob" ip=1.2.3.4'}]
