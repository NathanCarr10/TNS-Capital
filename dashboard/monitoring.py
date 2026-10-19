"""Reads the monitoring stack for the dashboard's Monitoring view.

- Metrics: VictoriaMetrics, queried in PromQL (MONITORING_METRICS_URL)
- Alerts: vmalert, which evaluates observability/vmalert/alerts.yml (MONITORING_ALERTS_URL)
- Logs: VictoriaLogs, queried in LogsQL (MONITORING_LOGS_URL)

Docker Compose runs the stack under the "observability" profile and sets the three URLs.
Without them the stack is treated as not running, and nothing is requested. Every answer
comes back as a small DataFrame; no Streamlit code here.
"""

import json
import os
import time

import pandas as pd
import requests

METRICS_URL = os.getenv("MONITORING_METRICS_URL")
ALERTS_URL = os.getenv("MONITORING_ALERTS_URL")
LOGS_URL = os.getenv("MONITORING_LOGS_URL")
TIMEOUT_SECONDS = 5

# The Compose services whose logs are collected, for the log search's filter
SERVICES = ["app", "auth-service", "broker-simulator", "dashboard", "etl", "frontend", "kafka", "postgres"]
LEVELS = ["error", "warn", "info", "debug"]

# Saved log searches: (label, text to find, levels)
LOG_PRESETS = {
    "Failed logins (A09-2)": ("Login failed", []),
    "Refresh token reuse (A09-1)": ("Refresh token reuse", []),
    "Access denied by the API": ("Access denied", []),
    "Errors, every service": ("", ["error"]),
}

# PromQL, by panel. Rates are per minute, which reads better than per second at this volume.
ORDERS_ACCEPTED = "sum by (side) (rate(trading_orders_accepted_total[5m])) * 60"
ORDERS_FINISHED = "sum by (status) (rate(trading_orders_finished_total[5m])) * 60"
FILL_TIME = ('label_set(histogram_quantile(0.5, sum by (le) (rate(trading_order_fill_time_seconds_bucket[5m]))), '
             '"quantile", "median") or label_set(histogram_quantile(0.95, sum by (le) '
             '(rate(trading_order_fill_time_seconds_bucket[5m]))), "quantile", "95th percentile")')
DLQ_PENDING = "trading_dlq_pending"
CONSUMER_LAG = "sum by (group) (kafka_consumer_group_lag)"
LOGINS = "sum by (outcome) (rate(auth_logins_total[5m])) * 60"
API_REFUSED = 'sum by (status) (rate(http_server_requests_seconds_count{job="api", status=~"401|403|5.."}[5m])) * 60'
HEAP_USED = ('sum by (job) (jvm_memory_used_bytes{area="heap"}) / 2^20 '
             'or sum by (job) (nodejs_heap_used_bytes) / 2^20')

# Headline numbers over the selected window ({window} is a PromQL duration such as 6h)
HEADLINES = {
    "orders": "sum(increase(trading_orders_accepted_total[{window}]))",
    "fill_p95": "histogram_quantile(0.95, sum by (le) (increase(trading_order_fill_time_seconds_bucket[{window}])))",
    "dlq": "sum(trading_dlq_pending)",
    "lag": "max(kafka_consumer_group_lag)",
    "failed_logins": 'sum(increase(auth_logins_total{{outcome="failure"}}[{window}]))',
    "token_reuse": "sum(increase(auth_refresh_token_reuse_total[{window}]))",
    "api_refused": 'sum(increase(http_server_requests_seconds_count{{job="api", status=~"401|403"}}[{window}]))',
    "api_errors": 'sum(increase(http_server_requests_seconds_count{{job="api", status=~"5.."}}[{window}]))',
}
TARGETS_UP = "up"
STREAM_STATE = "marketdata_stream_state == 1"


class Unavailable(Exception):
    """The monitoring stack is not configured or did not answer; the message is safe to show."""


def configured():
    return bool(METRICS_URL and ALERTS_URL and LOGS_URL)


def _get(base, path, params):
    if not base:
        raise Unavailable("The monitoring stack is not configured.")
    try:
        response = requests.get(f"{base}{path}", params=params, timeout=TIMEOUT_SECONDS)
        response.raise_for_status()
    except requests.RequestException as error:
        raise Unavailable(f"{base} did not answer. Is the observability profile running?") from error
    return response


def _prometheus(path, params):
    body = _get(METRICS_URL, path, params).json()
    if body.get("status") != "success":
        raise Unavailable(f"The metrics query failed: {body.get('error', 'unknown error')}")
    return body["data"]["result"]


def instant(query):
    """One value per series now: the series' labels as columns, plus value."""
    return parse_instant(_prometheus("/api/v1/query", {"query": query}))


def value(query, default=None):
    """A query's single value, or default when it has no data."""
    frame = instant(query)
    return default if frame.empty else frame["value"].iloc[0]


def series(query, minutes, label):
    """Values over the last minutes, one row per point: time, the series' label, value."""
    end = time.time()
    params = {"query": query, "start": end - minutes * 60, "end": end, "step": step_seconds(minutes)}
    return parse_range(_prometheus("/api/v1/query_range", params), label)


def step_seconds(minutes):
    """About 120 points per chart, never under the 15 s scrape interval."""
    return max(15, minutes * 60 // 120)


def parse_instant(result):
    rows = [{**item["metric"], "value": float(item["value"][1])} for item in result]
    return pd.DataFrame(rows, columns=None if rows else ["value"])


def parse_range(result, label):
    rows = [{"time": pd.Timestamp(float(stamp), unit="s"), label: item["metric"].get(label, ""), "value": float(point)}
            for item in result for stamp, point in item["values"]]
    frame = pd.DataFrame(rows, columns=["time", label, "value"])
    # A quantile over no fills is NaN: a gap in the line, not a zero
    return frame.dropna(subset=["value"])


def alerts():
    """Alerts firing or pending now, most severe first."""
    body = _get(ALERTS_URL, "/api/v1/alerts", None).json()
    return parse_alerts(body.get("data", {}).get("alerts", []))


SEVERITY_ORDER = {"critical": 0, "warning": 1}
ALERT_COLUMNS = ["name", "state", "severity", "owasp", "since", "summary", "action"]


def parse_alerts(items):
    rows = [{
        "name": item.get("name"),
        "state": item.get("state"),
        "severity": item.get("labels", {}).get("severity", "warning"),
        "owasp": item.get("labels", {}).get("owasp", ""),
        "since": pd.to_datetime(item.get("activeAt"), utc=True, errors="coerce").tz_localize(None),
        "summary": item.get("annotations", {}).get("summary", ""),
        "action": item.get("annotations", {}).get("action", ""),
    } for item in items]
    frame = pd.DataFrame(rows, columns=ALERT_COLUMNS)
    if frame.empty:
        return frame
    frame["rank"] = frame["severity"].map(SEVERITY_ORDER).fillna(len(SEVERITY_ORDER))
    return frame.sort_values(["rank", "since"]).drop(columns="rank").reset_index(drop=True)


def logsql(minutes, services=(), levels=(), text="", limit=200):
    """A LogsQL query for the log search, newest lines first.

    Services and levels come from fixed lists. The text is a quoted phrase, so it cannot
    add filters or pipes of its own.
    """
    parts = [f"_time:{int(minutes)}m"]
    if services:
        parts.append("{service in(" + ",".join(json.dumps(s) for s in services) + ")}")
    if levels:
        parts.append("level:in(" + ",".join(json.dumps(level) for level in levels) + ")")
    if text.strip():
        parts.append(json.dumps(text.strip()))
    return " ".join(parts) + f" | sort by (_time desc) | limit {int(limit)} | fields _time, service, level, _msg"


def logs(minutes, services=(), levels=(), text="", limit=200):
    """Matching log lines: time, service, level, message."""
    response = _get(LOGS_URL, "/select/logsql/query", {"query": logsql(minutes, services, levels, text, limit)})
    return parse_logs(response.text)


def parse_logs(body):
    rows = [json.loads(line) for line in body.splitlines() if line.strip()]
    frame = pd.DataFrame([{
        "time": pd.to_datetime(row.get("_time"), utc=True, errors="coerce").tz_localize(None),
        "service": row.get("service", ""),
        "level": row.get("level", ""),
        "message": row.get("_msg", ""),
    } for row in rows], columns=["time", "service", "level", "message"])
    return frame
