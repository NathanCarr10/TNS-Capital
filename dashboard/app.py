"""TNS Capital trading dashboard (Streamlit).

It shows every user's trades, so it is for admins only: users sign in through
the auth service and only a token with the ADMIN role opens the page. Support
staff (SUPPORT role) work in the Angular trading app instead; the two roles are
kept apart (docs/DECISIONS.md, "Roles").

A reload keeps the admin signed in: the tokens stay on the server (sessions.py) and
the browser keeps only a session id in a cookie, for up to 8 hours.

The sidebar switches between the trading insights and the Monitoring view
(monitoring_page.py): alerts, metrics and logs from the monitoring stack.

Run with:  streamlit run app.py
"""

import pandas as pd
import plotly.express as px
import streamlit as st

import auth
import data
import insights
import monitoring_page
import sessions
import theme

st.set_page_config(page_title="TNS Capital Dashboard", page_icon=str(theme.LOGO), layout="wide")
theme.apply()

VALUE_LABEL = "Value ($)"
ASSET_CLASS_LABEL = "Asset class"
FILL_RATE_LABEL = "Fill rate"


@st.cache_data(ttl=30)
def load():
    return data.load_all()


def money(value):
    return "n/a" if value is None else f"${value:,.2f}"


def percent(value):
    return "n/a" if value is None else f"{value:.0%}"


def duration(seconds):
    return "n/a" if seconds is None else f"{seconds:.1f} s"


def utc_now():
    """Now as a naive UTC timestamp, to compare with the database's UTC timestamps."""
    return pd.Timestamp.now(tz="UTC").tz_localize(None)


def sign_out():
    st.session_state.pop("token", None)
    session_id = st.session_state.pop("session_id", None)
    if session_id:
        refresh_token = sessions.STORE.remove(session_id)
        if refresh_token:
            auth.logout(refresh_token)  # revoked at the auth service too
        st.session_state["cookie"] = None  # cleared on the next run


def resume_session():
    """After a reload: the browser's session cookie leads back to the admin's tokens."""
    session_id = sessions.browser_cookie()
    token = sessions.STORE.access_token(session_id)
    if token:
        st.session_state["session_id"] = session_id
        st.session_state["token"] = token


def write_cookie():
    """Sets or clears the session cookie when sign-in or sign-out asked for it."""
    if "cookie" in st.session_state:
        st.html(sessions.cookie_script(st.session_state.pop("cookie")), unsafe_allow_javascript=True)


def signed_in_claims():
    """The token's claims, refreshing an expired token once; None (and signed out) if that fails."""
    token = st.session_state["token"]
    try:
        return auth.verify(token)  # every rerun, so an expired session ends here
    except auth.AuthError as error:
        message = str(error)
    fresh = sessions.STORE.refresh(st.session_state.get("session_id"), token, auth.refresh)
    if fresh:
        st.session_state["token"] = fresh
        try:
            return auth.verify(fresh)
        except auth.AuthError as error:
            message = str(error)
    sign_out()
    st.session_state["auth_message"] = message
    return None


def require_admin():
    """Stop the page unless an admin is signed in; show the sign-in form if nobody is."""
    write_cookie()
    if "token" not in st.session_state:
        resume_session()
    if st.session_state.get("token"):
        claims = signed_in_claims()
        if claims:
            if auth.is_admin(claims):
                return claims
            st.error(f"Signed in as {claims['sub']}, but this dashboard is for admins only.")
            if auth.is_support(claims):
                st.info("Support staff look after traders' accounts in the trading app, not here.")
            st.button("Sign out", on_click=sign_out)
            st.stop()

    # One centred card, like the trading app's login page
    theme.sign_in_page()
    _, card, _ = st.columns([1, 1, 1])
    with card:
        theme.logo(width=260)
        st.title("Analytics sign in")
        st.caption("Admins only. Sign in with your TNS Capital account.")
        if message := st.session_state.pop("auth_message", None):
            st.warning(message)
        with st.form("sign_in"):
            username = st.text_input("Username")
            password = st.text_input("Password", type="password")
            submitted = st.form_submit_button("Sign in", type="primary", width="stretch")
    if submitted:
        try:
            access_token, refresh_token = auth.login(username, password)
        except auth.AuthError as error:
            card.error(str(error))
        else:
            session_id = sessions.STORE.create(access_token, refresh_token)
            st.session_state.update(token=access_token, session_id=session_id, cookie=session_id)
            st.rerun()
    st.stop()


admin = require_admin()
with st.sidebar:
    theme.logo()
    view = st.segmented_control("View", ["Trading", "Monitoring"], default="Trading", key="view",
                                width="stretch")
    monitoring_page.alert_badge()
    st.divider()
    st.write(f"Signed in as **{admin['sub']}**")
    st.button("Sign out", on_click=sign_out, icon=":material/logout:", width="stretch")

if view == "Monitoring":
    monitoring_page.render()
    st.stop()

try:
    frames = load()
except Exception as error:  # the database is the only thing that can fail here
    st.error(f"Could not reach the database: {error}")
    st.stop()

theme.eyebrow("Admin analytics")
st.title("Trading insights")

source = st.radio(
    "Data source",
    ["Live (trading tables, updated by the Kafka consumer)", "Batch (analytics schema, loaded by the nightly ETL)"],
    horizontal=True,
)
use_batch = source.startswith("Batch")
if use_batch and not frames["has_batch"]:
    st.warning("The ETL has not run yet, so there is no batch data. Run `python etl/pipeline.py`.")
trades = frames["batch"] if use_batch else frames["live"]
positions = frames["positions"]

# Headline numbers
rate = insights.fill_rate(trades)
average = insights.average_trade_size(trades)
gap = insights.not_yet_in_batch(frames["live"], frames["batch"])
col1, col2, col3, col4 = st.columns(4)
col1.metric("Orders", f"{len(trades):,}")
col2.metric(FILL_RATE_LABEL, percent(rate))
col3.metric("Average trade size", money(average))
col4.metric("Orders not yet in batch", f"{len(gap):,}", help="Live orders the next ETL run will load")

# Key insights in words, for a non-technical audience
st.subheader("Key insights")
active = insights.most_active_accounts(trades, top=1)
exposure = insights.exposure_by_instrument(positions)
points = []
if rate is not None:
    points.append(f"**{percent(rate)}** of finished orders were filled; the rest were rejected or cancelled.")
if not active.empty:
    top = active.iloc[0]
    points.append(f"**{top.holder_name}** ({top.account_number}) is the most active account with "
                  f"**{top.orders}** orders and {money(top.filled_value)} filled.")
if not exposure.empty:
    biggest = exposure.iloc[0]
    points.append(f"The largest holding is **{biggest.symbol}** ({biggest.asset_class}), "
                  f"**{percent(biggest.share)}** of all exposure at cost.")
if average is not None:
    points.append(f"A filled trade is worth **{money(average)}** on average.")
st.markdown("\n".join(f"- {point}" for point in points) or "No trades yet.")

# Charts
left, right = st.columns(2)
volume = insights.trade_volume_by_date(trades)
theme.plot(left, px.bar(volume, x="date", y="value", hover_data=["trades"],
                        title="Filled trade value by day", labels={"value": VALUE_LABEL, "date": "Day"}))
statuses = insights.status_breakdown(trades)
theme.plot(right, px.pie(statuses, names="status", values="orders", title="Orders by status", hole=0.4))

left, right = st.columns(2)
top_accounts = insights.most_active_accounts(trades)
theme.plot(left, px.bar(top_accounts, x="orders", y="holder_name", orientation="h",
                        hover_data=["account_number", "filled_value"], title="Most active accounts",
                        labels={"orders": "Orders", "holder_name": ""}))
theme.plot(right, px.bar(exposure, x="symbol", y="exposure", color="asset_class",
                         title="Exposure by instrument (cost basis)",
                         labels={"exposure": VALUE_LABEL, "symbol": "Instrument", "asset_class": ASSET_CLASS_LABEL}))

mix = insights.value_by_asset_class(trades)
theme.plot(st, px.bar(mix, x="asset_class", y="value", color="side", barmode="group",
                      title="Buy and sell value by asset class",
                      labels={"value": VALUE_LABEL, "asset_class": ASSET_CLASS_LABEL, "side": "Side"}))

with st.expander("Orders not yet loaded by the ETL"):
    st.dataframe(gap, width="stretch")

# Operations: always from the live tables, since the batch source has no reasons or fill times
live = frames["live"]
st.subheader("Why orders are rejected")
st.caption("From the live trading tables, whichever data source is selected above.")
reasons = insights.rejection_reasons(live)
if reasons.empty:
    st.info("No orders have been rejected.")
else:
    left, right = st.columns(2)
    theme.plot(left, px.bar(reasons, x="orders", y="category", orientation="h", hover_data=["share"],
                            title="Rejected orders by reason", labels={"orders": "Orders", "category": ""}))
    right.dataframe(
        insights.rejected_orders(live)[["created_on", "username", "symbol", "side", "quantity", "category",
                                        "status_reason"]],
        width="stretch", hide_index=True,
        column_config={"created_on": st.column_config.DatetimeColumn("When (UTC)", format="D MMM, HH:mm:ss"),
                       "username": "User", "symbol": "Instrument", "side": "Side", "quantity": "Quantity",
                       "category": "Reason", "status_reason": "Details"})

st.subheader("Order pipeline health")
st.caption("From the live trading tables. Dead letters are read-only here: "
           "support retries or dismisses them on the trading app's Dead Letters page.")
timing = insights.fill_time_summary(live) or {"median": None, "p95": None}
stuck = insights.stuck_orders(live, utc_now())
dlq = frames["dlq"]
col1, col2, col3, col4 = st.columns(4)
col1.metric("Median time to fill", duration(timing["median"]),
            help="From order to first execution, over filled orders")
col2.metric("Slowest 5% take over", duration(timing["p95"]), help="95th percentile time to fill")
col3.metric("Stuck orders", f"{len(stuck):,}",
            help=f"NEW for over {insights.STUCK_AFTER.seconds // 60} minutes; the executor settles orders in seconds")
col4.metric("Dead letters awaiting review", f"{int((dlq['status'] == 'PENDING').sum()):,}",
            help=f"{len(dlq):,} dead letters in total")
if not stuck.empty:
    st.warning(f"{len(stuck)} order(s) have been waiting for over {insights.STUCK_AFTER.seconds // 60} minutes. "
               "Check the Kafka consumer and the broker simulator.")
    st.dataframe(stuck[["created_on", "minutes", "username", "symbol", "side", "quantity", "order_id"]],
                 width="stretch", hide_index=True,
                 column_config={"created_on": st.column_config.DatetimeColumn("Placed (UTC)"),
                                "minutes": st.column_config.NumberColumn("Waiting (min)", format="%.0f")})

left, right = st.columns(2)
with left:
    st.markdown("**Slowest fills**")
    slowest = insights.fill_times(live).head(5)
    if slowest.empty:
        st.caption("No timed fills yet.")
    else:
        st.dataframe(slowest[["seconds", "username", "symbol", "side", "quantity", "created_on"]],
                     width="stretch", hide_index=True,
                     column_config={"seconds": st.column_config.NumberColumn("Seconds", format="%.1f"),
                                    "created_on": st.column_config.DatetimeColumn("Placed (UTC)")})
with right:
    st.markdown("**Dead-letter queue by failure type**")
    if dlq.empty:
        st.caption("No message has failed processing.")
    else:
        st.dataframe(insights.dlq_summary(dlq), width="stretch", hide_index=True,
                     column_config={"failure_type": "Failure type", "messages": "Messages",
                                    "pending": "Awaiting review", "retryable": "Retryable"})

# Strategy Lab: orders the strategies placed, next to everyone's own
st.subheader("Automated strategies")
split = insights.strategy_vs_trader(trades)
by_strategy = insights.strategy_trades(trades)
if split.empty or "Strategy" not in set(split["placed_by"]):
    st.info("No strategy has placed an order yet. Traders switch strategies on in Strategy Lab.")
else:
    share = split.set_index("placed_by")["orders"]
    col1, col2, col3 = st.columns(3)
    col1.metric("Orders placed by strategies", f"{int(share.get('Strategy', 0)):,}")
    col2.metric("Share of all orders", percent(share.get("Strategy", 0) / share.sum()))
    col3.metric("Strategies that traded", f"{by_strategy['strategy_name'].nunique():,}")
    st.dataframe(split, width="stretch", hide_index=True,
                 column_config={"placed_by": "Placed by", "orders": "Orders", "filled": "Filled",
                                "fill_rate": st.column_config.NumberColumn(FILL_RATE_LABEL, format="percent"),
                                "traded_value": st.column_config.NumberColumn("Filled value ($)", format="dollar")})
    st.dataframe(by_strategy, width="stretch", hide_index=True,
                 column_config={"strategy_name": "Strategy", "username": "Owner", "symbol": "Instrument",
                                "orders": "Orders", "filled": "Filled",
                                "buy_value": st.column_config.NumberColumn("Bought ($)", format="dollar"),
                                "sell_value": st.column_config.NumberColumn("Sold ($)", format="dollar"),
                                "last_order": st.column_config.DatetimeColumn("Last order",
                                                                              format="D MMM YYYY, HH:mm")})

# What each user has traded
st.subheader("Trading by user")
by_user = insights.trades_by_user(trades)
if by_user.empty:
    st.info("No user has placed an order yet.")
else:
    st.dataframe(
        by_user, width="stretch", hide_index=True,
        column_config={
            "username": "User", "holder_name": "Holder", "account_number": "Account",
            "orders": "Orders", "filled": "Filled", "rejected": "Rejected",
            "fill_rate": st.column_config.NumberColumn(FILL_RATE_LABEL, format="percent"),
            "buy_value": st.column_config.NumberColumn("Bought ($)", format="dollar"),
            "sell_value": st.column_config.NumberColumn("Sold ($)", format="dollar"),
            "top_symbol": "Top instrument",
            "last_order": st.column_config.DatetimeColumn("Last order", format="D MMM YYYY, HH:mm"),
        })

    # Picked by holder name, keyed by account: names can repeat, and every account without a
    # login shares the username "(no login)"
    holders = by_user.sort_values(["holder_name", "account_number"])
    labels = dict(zip(holders["account_number"], holders["holder_name"] + " (" + holders["account_number"] + ")"))
    account = st.selectbox("See one account holder's orders", list(labels), index=None,
                           format_func=labels.get, placeholder="Choose an account holder")
    if account is not None:
        chosen = labels[account]
        theirs = trades[trades["account_number"] == account]
        col1, col2, col3 = st.columns(3)
        col1.metric("Their orders", f"{len(theirs):,}")
        col2.metric("Their fill rate", percent(insights.fill_rate(theirs)))
        col3.metric("Their average trade size", money(insights.average_trade_size(theirs)))
        theme.plot(st, px.bar(insights.value_by_asset_class(theirs), x="asset_class", y="value", color="side",
                              barmode="group", title=f"What {chosen} has bought and sold",
                              labels={"value": VALUE_LABEL, "asset_class": ASSET_CLASS_LABEL, "side": "Side"}))
        st.dataframe(theirs.sort_values("created_on", ascending=False)
                     [["created_on", "symbol", "side", "quantity", "price", "notional", "status"]],
                     width="stretch", hide_index=True)
