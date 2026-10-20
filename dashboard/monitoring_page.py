"""The Monitoring view: alerts, the order pipeline, security events, resources and log search.

Admins only, like the rest of the dashboard: app.py renders it after require_admin. The data
comes from the monitoring stack (monitoring.py). With Live on (the default) the panels below
the period picker rerun every 10 s, the scrape interval, without reloading the rest of the
page; answers are cached for 5 s, so each refresh asks the stack again.
"""

import pandas as pd
import plotly.express as px
import streamlit as st

import monitoring

RANGES = {"Last 15 minutes": 15, "Last hour": 60, "Last 6 hours": 360, "Last 24 hours": 1440, "Last 7 days": 10080}
REFRESH_SECONDS = monitoring.SCRAPE_SECONDS
PER_MINUTE = "Per minute"
STATE_ICONS = {"firing": "🔴", "pending": "🟡"}


@st.cache_data(ttl=5, show_spinner=False)
def cached_alerts():
    return monitoring.alerts()


@st.cache_data(ttl=5, show_spinner=False)
def cached_value(query):
    return monitoring.value(query)


@st.cache_data(ttl=5, show_spinner=False)
def cached_instant(query):
    return monitoring.instant(query)


@st.cache_data(ttl=5, show_spinner=False)
def cached_series(query, minutes, label):
    return monitoring.series(query, minutes, label)


def window(minutes):
    return f"{minutes // 60}h" if minutes % 60 == 0 else f"{minutes}m"


def count(number):
    return "n/a" if number is None else f"{number:,.0f}"


def seconds(number):
    # None, or NaN (e.g. a percentile with no samples yet)
    return "n/a" if pd.isna(number) else f"{number:.1f} s"


def alert_badge():
    """A line in the sidebar when alerts are firing, on every view. Silent without the stack."""
    if not monitoring.configured():
        return
    try:
        firing = cached_alerts().query("state == 'firing'")
    except monitoring.Unavailable:
        return
    if not firing.empty:
        st.error(f"{len(firing)} alert(s) firing. Open **Monitoring** to see them.", icon="🚨")


def line(column, query, minutes, label, title, y_label):
    frame = cached_series(query, minutes, label)
    if frame.empty:
        column.caption(f"**{title}**: no data in this period.")
        return
    figure = px.line(frame, x="time", y="value", color=label, title=title,
                     labels={"time": "Time (UTC)", "value": y_label, label: label.capitalize()})
    figure.update_traces(line_width=2)
    # One series: the title names it, so no legend
    figure.update_layout(showlegend=frame[label].nunique() > 1)
    column.plotly_chart(figure, width="stretch")


def render():
    st.title("Monitoring")
    if not monitoring.configured():
        st.info("The monitoring stack is not running. Start it with "
                "`docker compose --profile observability up -d`; it needs about 200 MB.")
        return

    left, right = st.columns([3, 1], vertical_alignment="bottom")
    minutes = RANGES[left.selectbox("Period", list(RANGES), key="monitoring_period")]
    live = right.toggle(f"Live (every {REFRESH_SECONDS} s)", value=True, key="monitoring_live")
    # A fragment reruns on its own, every REFRESH_SECONDS while live, leaving the rest of the page as it is
    st.fragment(render_panels, run_every=REFRESH_SECONDS if live else None)(minutes)


def render_panels(minutes):
    try:
        render_alerts()
        render_health()
        render_orders(minutes)
        render_security(minutes)
        render_resources(minutes)
    except monitoring.Unavailable as error:
        st.error(str(error))
    render_logs(minutes)
    st.caption(f"Updated {pd.Timestamp.now(tz='UTC'):%H:%M:%S} UTC.")


def render_alerts():
    st.subheader("Alerts")
    alerts = cached_alerts()
    if alerts.empty:
        st.success("No alerts are firing.", icon="✅")
        return
    shown = alerts.assign(state=alerts["state"].map(lambda state: f"{STATE_ICONS.get(state, '')} {state}"))
    st.dataframe(shown, width="stretch", hide_index=True,
                 column_config={"name": "Alert", "state": "State", "severity": "Severity", "owasp": "OWASP",
                                "since": st.column_config.DatetimeColumn("Since (UTC)", format="D MMM, HH:mm"),
                                "summary": "What happened", "action": "What to do"})
    st.caption("Pending alerts have met their condition but not yet for long enough to fire.")


def render_health():
    st.subheader("Services")
    targets = cached_instant(monitoring.TARGETS_UP)
    stream = cached_instant(monitoring.STREAM_STATE)
    columns = st.columns(len(targets) + 1 if not targets.empty else 1)
    for column, (_, target) in zip(columns, targets.sort_values("job").iterrows()):
        column.metric(target["job"], "✅ Up" if target["value"] == 1 else "❌ Down",
                      help="Whether the metrics store could scrape it on its last attempt")
    state = "n/a" if stream.empty else stream["state"].iloc[0]
    columns[-1].metric("Alpaca price stream", state.capitalize(),
                       help="STREAMING: live prices. CONNECTING: retrying, orders are priced over REST. "
                            "FAILED: refused for good (check the keys). OFF: no API keys.")


def render_orders(minutes):
    st.subheader("Order pipeline")
    span = window(minutes)
    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Orders accepted", count(cached_value(monitoring.HEADLINES["orders"].format(window=span))))
    col2.metric("Slowest 5% fill in over", seconds(cached_value(monitoring.HEADLINES["fill_p95"].format(window=span))),
                help="95th percentile of the time from accepting an order to filling it")
    col3.metric("Dead letters awaiting review", count(cached_value(monitoring.HEADLINES["dlq"])))
    col4.metric("Largest Kafka consumer lag", count(cached_value(monitoring.HEADLINES["lag"])),
                help="Messages a consumer group has not processed yet")

    left, right = st.columns(2)
    line(left, monitoring.ORDERS_ACCEPTED, minutes, "side", "Orders accepted", PER_MINUTE)
    line(right, monitoring.ORDERS_FINISHED, minutes, "status", "Orders finished, by outcome", PER_MINUTE)
    left, right = st.columns(2)
    line(left, monitoring.FILL_TIME, minutes, "quantile", "Time from accepting to filling", "Seconds")
    line(right, monitoring.CONSUMER_LAG, minutes, "group", "Kafka consumer lag", "Messages")
    left, _ = st.columns(2)
    line(left, monitoring.DLQ_PENDING, minutes, "job", "Dead letters awaiting review", "Messages")


def render_security(minutes):
    st.subheader("Security events")
    st.caption("OWASP A09: failed logins (A09-2), refresh token reuse (A09-1), refused and failed API requests.")
    span = window(minutes)
    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Failed logins", count(cached_value(monitoring.HEADLINES["failed_logins"].format(window=span))))
    col2.metric("Refresh tokens reused", count(cached_value(monitoring.HEADLINES["token_reuse"].format(window=span))),
                help="Each one revoked a session: the token may have been copied")
    col3.metric("API requests refused (401/403)",
                count(cached_value(monitoring.HEADLINES["api_refused"].format(window=span))))
    col4.metric("API server errors (5xx)", count(cached_value(monitoring.HEADLINES["api_errors"].format(window=span))))

    left, right = st.columns(2)
    line(left, monitoring.LOGINS, minutes, "outcome", "Logins, by outcome", PER_MINUTE)
    line(right, monitoring.API_REFUSED, minutes, "status", "API requests refused or failed, by status", PER_MINUTE)


def render_resources(minutes):
    st.subheader("Resources")
    left, _ = st.columns(2)
    line(left, monitoring.HEAP_USED, minutes, "job", "Heap in use, by service", "MiB")


def render_logs(minutes):
    st.subheader("Logs")
    st.caption("Every service's logs, kept for 7 days. Newest first, at most 200 lines.")
    preset = st.selectbox("Saved search", ["None", *monitoring.LOG_PRESETS], key="log_preset")
    text, levels = monitoring.LOG_PRESETS.get(preset, ("", []))
    left, middle, right = st.columns([2, 1, 1])
    text = left.text_input("Containing", value=text, key=f"log_text_{preset}",
                           placeholder='Words or a phrase, e.g. Login failed')
    services = middle.multiselect("Services", monitoring.SERVICES, key="log_services", placeholder="All")
    levels = right.multiselect("Levels", monitoring.LEVELS, default=levels, key=f"log_levels_{preset}",
                               placeholder="All")
    try:
        found = monitoring.logs(minutes, services, levels, text)
    except monitoring.Unavailable as error:
        st.error(str(error))
        return
    if found.empty:
        st.caption("No matching log lines in this period.")
        return
    st.dataframe(found, width="stretch", hide_index=True,
                 column_config={"time": st.column_config.DatetimeColumn("Time (UTC)", format="D MMM, HH:mm:ss"),
                                "service": "Service", "level": "Level",
                                "message": st.column_config.TextColumn("Message", width="large")})
