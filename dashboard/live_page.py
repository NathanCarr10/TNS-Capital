"""The Live view: what is happening on the platform now, and what needs an admin.

From the trading tables (data.load_live), which the Kafka consumer updates as each order is
processed; only they have rejection reasons, execution times, positions and dead letters.
"Needs attention" is the state right now, whatever the period; the rest covers the chosen
period. With Live on (the default) the panels rerun every 30 s without reloading the page.
Trends and all-time reports are on the Batch view (batch_page.py), the metrics stack's rates
on Monitoring.
"""

from datetime import timedelta

import plotly.express as px
import streamlit as st

import data
import insights
import theme
from formats import ASSET_CLASS_LABEL, FILL_RATE_LABEL, VALUE_LABEL, duration, money, percent, utc_now

PERIODS = {"Last hour": timedelta(hours=1), "Last 24 hours": timedelta(days=1), "Last 7 days": timedelta(days=7)}
REFRESH_SECONDS = 30
LATEST_ORDERS = 20
WHEN_PLACED = "Placed (UTC)"


@st.cache_data(ttl=10, show_spinner=False)
def load():
    return data.load_live()


def render():
    theme.eyebrow("Admin analytics")
    st.title("Live trading")
    st.caption("From the trading tables, as the Kafka consumer processes each order.")
    left, right = st.columns([3, 1], vertical_alignment="bottom")
    period = left.selectbox("Period", list(PERIODS), index=1, key="live_period")
    live = right.toggle(f"Live (every {REFRESH_SECONDS} s)", value=True, key="live_refresh")
    # A fragment reruns on its own, every REFRESH_SECONDS while live, leaving the rest of the page as it is
    st.fragment(render_panels, run_every=REFRESH_SECONDS if live else None)(period)


def render_panels(period):
    try:
        frames = load()
    except Exception as error:  # the database is the only thing that can fail here
        st.error(f"Could not reach the database: {error}")
        return
    now = utc_now()
    trades = frames["trades"]
    recent = insights.in_period(trades, now, PERIODS[period])
    render_attention(trades, frames["dlq"], now)
    render_activity(recent, period)
    render_rejections(recent)
    render_fill_times(recent)
    render_traders(recent)
    render_latest(trades)
    render_holdings(frames["positions"])
    st.caption(f"Updated {now:%H:%M:%S} UTC.")


def render_attention(trades, dlq, now):
    st.subheader("Needs attention")
    stuck = insights.stuck_orders(trades, now)
    pending = int((dlq["status"] == "PENDING").sum())
    minutes = insights.STUCK_AFTER.seconds // 60
    col1, col2 = st.columns(2)
    col1.metric("Stuck orders", f"{len(stuck):,}",
                help=f"NEW for over {minutes} minutes; the executor settles orders in seconds")
    col2.metric("Dead letters awaiting review", f"{pending:,}", help=f"{len(dlq):,} dead letters in total")
    if stuck.empty and not pending:
        st.success("Nothing is waiting: no stuck orders and no dead letters to review.", icon="✅")
    if not stuck.empty:
        st.warning(f"{len(stuck)} order(s) have been waiting for over {minutes} minutes. "
                   "Check the Kafka consumer and the broker simulator.")
        st.dataframe(stuck[["created_on", "minutes", "email", "symbol", "side", "quantity", "order_id"]],
                     width="stretch", hide_index=True,
                     column_config={"created_on": st.column_config.DatetimeColumn(WHEN_PLACED),
                                    "minutes": st.column_config.NumberColumn("Waiting (min)", format="%.0f")})
    if pending:
        st.caption("Dead letters are read-only here: support retries or dismisses them on the trading app's "
                   "Dead Letters page.")
        st.dataframe(insights.dlq_summary(dlq), width="stretch", hide_index=True,
                     column_config={"failure_type": "Failure type", "messages": "Messages",
                                    "pending": "Awaiting review", "retryable": "Retryable"})


def render_activity(recent, period):
    st.subheader(period)
    done = insights.filled(recent)
    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Orders", f"{len(recent):,}")
    col2.metric(FILL_RATE_LABEL, percent(insights.fill_rate(recent)),
                help="Share of finished orders that filled; orders still NEW are left out")
    col3.metric("Filled value", money(float(done["notional"].sum())))
    col4.metric("Rejected", f"{int((recent['status'] == 'REJECTED').sum()):,}")


def render_rejections(recent):
    st.markdown("**Why orders are rejected**")
    reasons = insights.rejection_reasons(recent)
    if reasons.empty:
        st.caption("No orders were rejected in this period.")
        return
    left, right = st.columns(2)
    theme.plot(left, px.bar(reasons, x="orders", y="category", orientation="h", hover_data=["share"],
                            title="Rejected orders by reason", labels={"orders": "Orders", "category": ""}))
    right.dataframe(
        insights.rejected_orders(recent)[["created_on", "email", "symbol", "side", "quantity", "category",
                                          "status_reason"]],
        width="stretch", hide_index=True,
        column_config={"created_on": st.column_config.DatetimeColumn("When (UTC)", format="D MMM, HH:mm:ss"),
                       "email": "Email", "symbol": "Instrument", "side": "Side", "quantity": "Quantity",
                       "category": "Reason", "status_reason": "Details"})


def render_fill_times(recent):
    st.markdown("**Time to fill**")
    timing = insights.fill_time_summary(recent) or {"median": None, "p95": None}
    left, right = st.columns([1, 2])
    left.metric("Median time to fill", duration(timing["median"]),
                help="From order to first execution, over filled orders")
    left.metric("Slowest 5% take over", duration(timing["p95"]), help="95th percentile time to fill")
    slowest = insights.fill_times(recent).head(5)
    if slowest.empty:
        right.caption("No timed fills in this period.")
    else:
        right.dataframe(slowest[["seconds", "email", "symbol", "side", "quantity", "created_on"]],
                        width="stretch", hide_index=True,
                        column_config={"seconds": st.column_config.NumberColumn("Seconds", format="%.1f"),
                                       "email": "Email", "symbol": "Instrument", "side": "Side",
                                       "quantity": "Quantity",
                                       "created_on": st.column_config.DatetimeColumn(WHEN_PLACED)})


def render_traders(recent):
    st.markdown("**Who is trading**")
    if recent.empty:
        st.caption("No orders in this period.")
        return
    left, right = st.columns(2)
    theme.plot(left, px.bar(insights.most_active_accounts(recent), x="orders", y="holder_name", orientation="h",
                            hover_data=["account_number", "filled_value"], title="Most active accounts",
                            labels={"orders": "Orders", "holder_name": ""}))
    with right:
        strategies = insights.strategy_trades(recent)
        if strategies.empty:
            st.caption("No strategy placed an order in this period.")
        else:
            st.dataframe(strategies[["strategy_name", "email", "symbol", "orders", "filled", "last_order"]],
                         width="stretch", hide_index=True,
                         column_config={"strategy_name": "Strategy", "email": "Owner", "symbol": "Instrument",
                                        "orders": "Orders", "filled": "Filled",
                                        "last_order": st.column_config.DatetimeColumn("Last order (UTC)",
                                                                                      format="D MMM, HH:mm")})


def render_latest(trades):
    st.markdown("**Latest orders**")
    if trades.empty:
        st.caption("No order has been placed yet.")
        return
    latest = trades.sort_values("created_on", ascending=False).head(LATEST_ORDERS)
    st.dataframe(latest[["created_on", "email", "symbol", "side", "quantity", "price", "status", "status_reason",
                         "strategy_name"]],
                 width="stretch", hide_index=True,
                 column_config={"created_on": st.column_config.DatetimeColumn(WHEN_PLACED, format="D MMM, HH:mm:ss"),
                                "email": "Email", "symbol": "Instrument", "side": "Side", "quantity": "Quantity",
                                "price": st.column_config.NumberColumn("Price ($)", format="dollar"),
                                "status": "Status", "status_reason": "Reason", "strategy_name": "Strategy"})


def render_holdings(positions):
    st.subheader("Holdings now")
    exposure = insights.exposure_by_instrument(positions)
    if exposure.empty:
        st.caption("Nobody holds a position.")
        return
    theme.plot(st, px.bar(exposure, x="symbol", y="exposure", color="asset_class",
                          title="Exposure by instrument (cost basis)",
                          labels={"exposure": VALUE_LABEL, "symbol": "Instrument", "asset_class": ASSET_CLASS_LABEL}))
