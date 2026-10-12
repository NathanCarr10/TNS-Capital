"""Business insights for the dashboard, as plain pandas functions.

Every function takes DataFrames and returns a DataFrame or a number, so it can
be unit tested without a database or a browser. Trades have the columns of
analytics.fact_trades (see etl/schema.sql), plus asset_class.
"""

import pandas as pd

FINAL_STATUSES = ["FILLED", "REJECTED", "CANCELLED"]


def filled(trades):
    """Only the trades that executed."""
    return trades[trades["status"] == "FILLED"]


def trade_volume_by_date(trades):
    """Number and value of filled trades per day (spec 7.3: Trade Volume)."""
    done = filled(trades)
    if done.empty:
        return pd.DataFrame(columns=["date", "trades", "value"])
    by_day = done.groupby(pd.to_datetime(done["created_on"]).dt.date).agg(
        trades=("order_id", "count"), value=("notional", "sum"))
    return by_day.rename_axis("date").reset_index()


def most_active_accounts(trades, top=5):
    """Accounts with the most orders, with the value they filled (spec 7.3)."""
    if trades.empty:
        return pd.DataFrame(columns=["account_number", "holder_name", "orders", "filled_value"])
    trades = trades.assign(filled_value=trades["notional"].where(trades["status"] == "FILLED", 0))
    activity = trades.groupby(["account_number", "holder_name"], as_index=False).agg(
        orders=("order_id", "count"), filled_value=("filled_value", "sum"))
    return activity.sort_values(["orders", "filled_value"], ascending=False).head(top).reset_index(drop=True)


def fill_rate(trades):
    """Share of finished orders that were filled, from 0 to 1 (spec 7.3: Fill Rate).

    NEW orders are still waiting, so they are left out. Returns None when no
    order has finished yet.
    """
    finished = trades[trades["status"].isin(FINAL_STATUSES)]
    if finished.empty:
        return None
    return float((finished["status"] == "FILLED").mean())


def status_breakdown(trades):
    """How many orders ended in each status."""
    counts = trades["status"].value_counts().rename_axis("status").reset_index(name="orders")
    return counts.sort_values("status").reset_index(drop=True)


def exposure_by_instrument(positions):
    """Cost-basis value held per instrument across all accounts (spec 7.3: Exposure).

    positions has symbol, asset_class, quantity and average_cost.
    """
    if positions.empty:
        return pd.DataFrame(columns=["symbol", "asset_class", "quantity", "exposure", "share"])
    held = positions.assign(exposure=positions["quantity"] * positions["average_cost"])
    exposure = held.groupby(["symbol", "asset_class"], as_index=False)[["quantity", "exposure"]].sum()
    exposure["share"] = exposure["exposure"] / exposure["exposure"].sum()
    return exposure.sort_values("exposure", ascending=False).reset_index(drop=True)


def average_trade_size(trades):
    """Mean value of a filled trade (spec 7.3: Average Trade Size); None if none filled."""
    done = filled(trades)
    if done.empty:
        return None
    return float(done["notional"].mean())


def value_by_asset_class(trades):
    """Filled value per asset class and side, for the buy/sell mix chart."""
    done = filled(trades)
    if done.empty:
        return pd.DataFrame(columns=["asset_class", "side", "value"])
    mix = done.groupby(["asset_class", "side"], as_index=False).agg(value=("notional", "sum"))
    return mix.sort_values(["asset_class", "side"]).reset_index(drop=True)


def not_yet_in_batch(live_orders, batch_trades):
    """Live orders the nightly ETL has not loaded yet: the gap between the two sources."""
    return live_orders[~live_orders["order_id"].isin(batch_trades["order_id"])].reset_index(drop=True)
