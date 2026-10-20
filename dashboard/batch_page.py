"""The Batch view: how trading has gone over time, as of the nightly ETL's last run.

From the analytics schema (data.load_batch): every order the ETL loaded, Alpaca's daily
prices and the orders the latest run refused. It reports the whole history: trends by day,
week or month, what is traded, each user's trading and the strategies'. What is happening
now, and what needs an admin, is on the Live view (live_page.py).
"""

from datetime import timedelta

import pandas as pd
import plotly.express as px
import streamlit as st

import data
import insights
import live_page
import theme
from formats import ASSET_CLASS_LABEL, FILL_RATE_LABEL, VALUE_LABEL, money, percent

PRICE_LEAD_IN = timedelta(days=30)  # market prices shown before an instrument's first fill


@st.cache_data(ttl=300, show_spinner=False)
def load():
    return data.load_batch()  # the ETL loads once a day, so this changes rarely


def render():
    theme.eyebrow("Admin analytics")
    st.title("Trading history")
    try:
        batch = load()
        live = live_page.load()["trades"]
    except Exception as error:  # the database is the only thing that can fail here
        st.error(f"Could not reach the database: {error}")
        return
    if not batch["available"]:
        st.warning("The ETL has not run yet, so there is no batch data. Run `docker-compose run --rm etl python pipeline.py`.")
        return

    trades = batch["trades"]
    gap = insights.not_yet_in_batch(live, trades)
    if trades.empty:
        st.caption("Loaded by the nightly ETL, which has found no orders yet.")
    else:
        st.caption(f"Loaded by the nightly ETL. The latest order it loaded was placed "
                   f"{trades['created_on'].max():%d %b %Y, %H:%M} UTC; {len(gap):,} newer order(s) "
                   "load on its next run.")
    render_headlines(trades)
    render_key_insights(trades)
    render_trends(trades)
    render_mix(trades, batch["prices"])
    render_users(trades)
    render_strategies(trades)
    render_data_quality(batch["etl_rejects"], gap)


def render_headlines(trades):
    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Orders", f"{len(trades):,}")
    col2.metric(FILL_RATE_LABEL, percent(insights.fill_rate(trades)))
    col3.metric("Filled value", money(float(insights.filled(trades)["notional"].sum())))
    col4.metric("Average trade size", money(insights.average_trade_size(trades)))


def render_key_insights(trades):
    """The main findings in words, for a non-technical audience."""
    st.subheader("Key insights")
    rate = insights.fill_rate(trades)
    average = insights.average_trade_size(trades)
    active = insights.most_active_accounts(trades, top=1)
    instruments = insights.value_by_instrument(trades, top=1)
    split = insights.strategy_vs_trader(trades).set_index("placed_by")["orders"]
    points = []
    if rate is not None:
        points.append(f"**{percent(rate)}** of finished orders were filled; the rest were rejected or cancelled.")
    if not active.empty:
        top = active.iloc[0]
        points.append(f"**{top.holder_name}** ({top.account_number}) is the most active account with "
                      f"**{top.orders}** orders and {money(top.filled_value)} filled.")
    if not instruments.empty:
        top = instruments.iloc[0]
        points.append(f"**{top.symbol}** ({top.asset_class}) is the most traded instrument, with "
                      f"{money(top.value)} filled over {top.trades} trades.")
    if average is not None:
        points.append(f"A filled trade is worth **{money(average)}** on average.")
    if split.get("Strategy", 0):
        points.append(f"Automated strategies placed **{percent(split['Strategy'] / split.sum())}** of all orders.")
    st.markdown("\n".join(f"- {point}" for point in points) or "No trades yet.")


def render_trends(trades):
    st.subheader("Trends")
    grouping = st.segmented_control("Group by", list(insights.TREND_FREQUENCIES), default="Day",
                                    key="batch_grouping") or "Day"
    periods = insights.trend(trades, insights.TREND_FREQUENCIES[grouping])
    if periods.empty:
        st.caption("No orders yet.")
        return
    left, right = st.columns(2)
    theme.plot(left, px.bar(periods, x="period", y="value", hover_data=["filled"],
                            title=f"Filled trade value by {grouping.lower()}",
                            labels={"value": VALUE_LABEL, "period": grouping, "filled": "Filled trades"}))
    fill = px.line(periods.dropna(subset=["fill_rate"]), x="period", y="fill_rate", markers=True,
                   hover_data=["orders"], title=f"Fill rate by {grouping.lower()}",
                   labels={"fill_rate": FILL_RATE_LABEL, "period": grouping, "orders": "Orders"})
    fill.update_yaxes(tickformat=".0%", range=[0, 1.05])
    theme.plot(right, fill)


def render_mix(trades, prices):
    st.subheader("What is traded")
    if insights.filled(trades).empty:
        st.caption("No order has filled yet.")
        return
    left, right = st.columns(2)
    theme.plot(left, px.bar(insights.value_by_asset_class(trades), x="asset_class", y="value", color="side",
                            barmode="group", title="Buy and sell value by asset class",
                            labels={"value": VALUE_LABEL, "asset_class": ASSET_CLASS_LABEL, "side": "Side"}))
    theme.plot(right, px.bar(insights.value_by_instrument(trades), x="symbol", y="value", color="asset_class",
                             hover_data=["trades"], title="Top instruments by filled value",
                             labels={"value": VALUE_LABEL, "symbol": "Instrument", "asset_class": ASSET_CLASS_LABEL,
                                     "trades": "Filled trades"}))
    render_fills_vs_market(insights.filled(trades), prices)


def render_fills_vs_market(fills, prices):
    """An instrument's daily close from Alpaca, with the prices our orders filled at."""
    st.markdown("**Fills against the market**")
    if prices.empty:
        st.caption("No daily prices loaded yet: the ETL needs the Alpaca API keys to load them.")
        return
    symbols = [symbol for symbol in insights.value_by_instrument(fills, top=None)["symbol"]
               if symbol in set(prices["symbol"])]
    if not symbols:
        st.caption("No filled order is for an instrument with daily prices.")
        return
    symbol = st.selectbox("Instrument", symbols, key="batch_price_symbol",
                          help="Instruments with fills, most traded first. A fill far from the close is worth a look.")
    theirs = fills[fills["symbol"] == symbol]
    closes = prices[prices["symbol"] == symbol]
    closes = closes[pd.to_datetime(closes["trade_date"]) >= theirs["created_on"].min() - PRICE_LEAD_IN]
    figure = px.line(closes.sort_values("trade_date"), x="trade_date", y="close",
                     title=f"{symbol}: daily close and our fills",
                     labels={"trade_date": "Day", "close": "Price ($)"})
    figure.data[0].name, figure.data[0].showlegend = "Market close", True
    for trace in px.scatter(theirs, x="created_on", y="price", color="side",
                            hover_data=["username", "quantity"]).data:
        figure.add_trace(trace)
    theme.plot(st, figure)


def render_users(trades):
    st.subheader("Trading by user")
    by_user = insights.trades_by_user(trades)
    if by_user.empty:
        st.info("No user has placed an order yet.")
        return
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
    if account is None:
        return
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


def render_strategies(trades):
    """Orders Strategy Lab's strategies placed, next to everyone's own."""
    st.subheader("Automated strategies")
    split = insights.strategy_vs_trader(trades)
    by_strategy = insights.strategy_trades(trades)
    if split.empty or "Strategy" not in set(split["placed_by"]):
        st.info("No strategy has placed an order yet. Traders switch strategies on in Strategy Lab.")
        return
    share = split.set_index("placed_by")["orders"]
    col1, col2, col3 = st.columns(3)
    col1.metric("Orders placed by strategies", f"{int(share.get('Strategy', 0)):,}")
    col2.metric("Share of all orders", percent(share.get("Strategy", 0) / share.sum()))
    col3.metric("Strategies that traded", f"{by_strategy['strategy_name'].nunique():,}")

    weekly = insights.trend(trades, "W").rename(columns={"strategy_orders": "Strategy", "trader_orders": "Trader"})
    adoption = weekly[["period", "Trader", "Strategy"]].melt(id_vars="period", value_vars=["Trader", "Strategy"], var_name="placed_by",
                           value_name="orders")
    theme.plot(st, px.bar(adoption, x="period", y="orders", color="placed_by", title="Orders by week, by who placed them",
                          labels={"period": "Week", "orders": "Orders", "placed_by": "Placed by"}))
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


def render_data_quality(rejects, gap):
    """What the batch source is missing: rows the ETL refused, and orders it has not loaded yet."""
    st.subheader("Data quality")
    col1, col2 = st.columns(2)
    col1.metric("Orders the latest ETL run refused", f"{len(rejects):,}",
                help="Rows that failed a validation rule; fixed at the source, they load on the next run")
    col2.metric("Orders not yet loaded", f"{len(gap):,}", help="Live orders the next ETL run will load")
    if not rejects.empty:
        st.dataframe(insights.etl_reject_reasons(rejects), width="stretch", hide_index=True,
                     column_config={"reject_reason": "Rule broken", "orders": "Orders"})
        with st.expander("Refused rows, as the ETL read them"):
            st.dataframe(rejects, width="stretch", hide_index=True)
    with st.expander("Orders not yet loaded by the ETL"):
        st.dataframe(gap, width="stretch", hide_index=True)
