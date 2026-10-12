"""TNS Capital trading dashboard (Streamlit).

Run with:  streamlit run app.py
"""

import plotly.express as px
import streamlit as st

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
