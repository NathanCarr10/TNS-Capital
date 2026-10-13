"""TNS Capital trading dashboard (Streamlit).

It shows every user's trades, so it is for admins only: users sign in through
the auth service and only a token with the ADMIN role opens the page.

Run with:  streamlit run app.py
"""

import plotly.express as px
import streamlit as st

import auth
import data
import insights

st.set_page_config(page_title="TNS Capital Dashboard", layout="wide")

VALUE_LABEL = "Value ($)"


@st.cache_data(ttl=30)
def load():
    return data.load_all()


def money(value):
    return "n/a" if value is None else f"${value:,.2f}"


def percent(value):
    return "n/a" if value is None else f"{value:.0%}"


def sign_out():
    st.session_state.pop("token", None)


def require_admin():
    """Stop the page unless an admin is signed in; show the sign-in form if nobody is."""
    token = st.session_state.get("token")
    if token:
        try:
            claims = auth.verify(token)  # every rerun, so an expired session ends here
        except auth.AuthError as error:
            sign_out()
            st.session_state["auth_message"] = str(error)
        else:
            if auth.is_admin(claims):
                return claims
            st.error(f"Signed in as {claims['sub']}, but this dashboard is for admins only.")
            st.button("Sign out", on_click=sign_out)
            st.stop()

    st.title("TNS Capital trading dashboard")
    st.caption("Admins only. Sign in with your TNS Capital account.")
    if message := st.session_state.pop("auth_message", None):
        st.warning(message)
    with st.form("sign_in"):
        username = st.text_input("Username")
        password = st.text_input("Password", type="password")
        submitted = st.form_submit_button("Sign in")
    if submitted:
        try:
            st.session_state["token"] = auth.login(username, password)
        except auth.AuthError as error:
            st.error(str(error))
        else:
            st.rerun()
    st.stop()


admin = require_admin()
with st.sidebar:
    st.write(f"Signed in as **{admin['sub']}**")
    st.button("Sign out", on_click=sign_out)

try:
    frames = load()
except Exception as error:  # the database is the only thing that can fail here
    st.error(f"Could not reach the database: {error}")
    st.stop()

st.title("TNS Capital trading dashboard")

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
col2.metric("Fill rate", percent(rate))
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
left.plotly_chart(px.bar(volume, x="date", y="value", hover_data=["trades"],
                         title="Filled trade value by day", labels={"value": VALUE_LABEL, "date": "Day"}),
                  width="stretch")
statuses = insights.status_breakdown(trades)
right.plotly_chart(px.pie(statuses, names="status", values="orders", title="Orders by status", hole=0.4),
                   width="stretch")

left, right = st.columns(2)
top_accounts = insights.most_active_accounts(trades)
left.plotly_chart(px.bar(top_accounts, x="orders", y="holder_name", orientation="h",
                         hover_data=["account_number", "filled_value"], title="Most active accounts",
                         labels={"orders": "Orders", "holder_name": ""}),
                  width="stretch")
right.plotly_chart(px.bar(exposure, x="symbol", y="exposure", color="asset_class",
                          title="Exposure by instrument (cost basis)",
                          labels={"exposure": VALUE_LABEL, "symbol": "Instrument", "asset_class": "Asset class"}),
                   width="stretch")

mix = insights.value_by_asset_class(trades)
st.plotly_chart(px.bar(mix, x="asset_class", y="value", color="side", barmode="group",
                       title="Buy and sell value by asset class",
                       labels={"value": VALUE_LABEL, "asset_class": "Asset class", "side": "Side"}),
                width="stretch")

with st.expander("Orders not yet loaded by the ETL"):
    st.dataframe(gap, width="stretch")

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
            "fill_rate": st.column_config.NumberColumn("Fill rate", format="percent"),
            "buy_value": st.column_config.NumberColumn("Bought ($)", format="dollar"),
            "sell_value": st.column_config.NumberColumn("Sold ($)", format="dollar"),
            "top_symbol": "Top instrument",
            "last_order": st.column_config.DatetimeColumn("Last order", format="D MMM YYYY, HH:mm"),
        })

    chosen = st.selectbox("See one user's orders", by_user["username"].tolist(), index=None,
                          placeholder="Choose a user")
    if chosen is not None:
        theirs = trades[trades["username"] == chosen]
        col1, col2, col3 = st.columns(3)
        col1.metric("Their orders", f"{len(theirs):,}")
        col2.metric("Their fill rate", percent(insights.fill_rate(theirs)))
        col3.metric("Their average trade size", money(insights.average_trade_size(theirs)))
        st.plotly_chart(px.bar(insights.value_by_asset_class(theirs), x="asset_class", y="value", color="side",
                               barmode="group", title=f"What {chosen} has bought and sold",
                               labels={"value": VALUE_LABEL, "asset_class": "Asset class", "side": "Side"}),
                        width="stretch")
        st.dataframe(theirs.sort_values("created_on", ascending=False)
                     [["created_on", "symbol", "side", "quantity", "price", "notional", "status"]],
                     width="stretch", hide_index=True)
