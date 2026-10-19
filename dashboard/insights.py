"""Business insights for the dashboard, as plain pandas functions.

Every function takes DataFrames and returns a DataFrame or a number, so it can
be unit tested without a database or a browser. Trades have the columns of
analytics.fact_trades (see etl/schema.sql), plus asset_class.
"""

from datetime import timedelta

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


def trades_by_user(trades):
    """What each user has traded, for the admin view: one row per user, busiest first.

    trades needs a username column (see data.py). fill_rate is NaN for a user
    with no finished orders yet; top_symbol is the instrument they filled the
    most value in, or None if nothing filled.
    """
    columns = ["username", "holder_name", "account_number", "orders", "filled", "rejected", "fill_rate",
               "buy_value", "sell_value", "top_symbol", "last_order"]
    if trades.empty:
        return pd.DataFrame(columns=columns)
    done = trades["status"] == "FILLED"
    flagged = trades.assign(
        filled=done,
        rejected=trades["status"] == "REJECTED",
        finished=trades["status"].isin(FINAL_STATUSES),
        buy_value=trades["notional"].where(done & (trades["side"] == "BUY"), 0),
        sell_value=trades["notional"].where(done & (trades["side"] == "SELL"), 0),
    )
    keys = ["username", "holder_name", "account_number"]
    users = flagged.groupby(keys, as_index=False).agg(
        orders=("order_id", "count"), filled=("filled", "sum"), rejected=("rejected", "sum"),
        finished=("finished", "sum"), buy_value=("buy_value", "sum"), sell_value=("sell_value", "sum"),
        last_order=("created_on", "max"))
    users["fill_rate"] = (users["filled"] / users["finished"]).where(users["finished"] > 0)

    filled_trades = filled(trades)
    if filled_trades.empty:
        users["top_symbol"] = None
    else:
        by_symbol = filled_trades.groupby(keys + ["symbol"], as_index=False)["notional"].sum()
        top = by_symbol.sort_values(["notional", "symbol"], ascending=[False, True]).drop_duplicates(keys)
        users = users.merge(top[keys + ["symbol"]].rename(columns={"symbol": "top_symbol"}), on=keys, how="left")
        users["top_symbol"] = users["top_symbol"].astype(object).where(users["top_symbol"].notna(), None)

    users = users.sort_values(["orders", "username"], ascending=[False, True]).reset_index(drop=True)
    return users[columns]


def placed_by(trades):
    """Who placed each order: "Strategy" (Strategy Lab) or "Trader" (by hand).

    Uses the strategy_name column from data.py; without it every order counts as the trader's.
    """
    if "strategy_name" not in trades:
        return pd.Series("Trader", index=trades.index)
    return trades["strategy_name"].notna().map({True: "Strategy", False: "Trader"})


def strategy_vs_trader(trades):
    """Orders placed by strategies next to everyone's own: one row each for "Strategy" and "Trader"."""
    columns = ["placed_by", "orders", "filled", "fill_rate", "traded_value"]
    if trades.empty:
        return pd.DataFrame(columns=columns)
    done = trades["status"] == "FILLED"
    flagged = trades.assign(placed_by=placed_by(trades), filled=done,
                            finished=trades["status"].isin(FINAL_STATUSES),
                            traded_value=trades["notional"].where(done, 0))
    summary = flagged.groupby("placed_by", as_index=False).agg(
        orders=("order_id", "count"), filled=("filled", "sum"), finished=("finished", "sum"),
        traded_value=("traded_value", "sum"))
    summary["fill_rate"] = (summary["filled"] / summary["finished"]).where(summary["finished"] > 0)
    return summary.sort_values("placed_by").reset_index(drop=True)[columns]


def strategy_trades(trades):
    """What each strategy has traded, busiest first: one row per strategy and owner."""
    columns = ["strategy_name", "username", "symbol", "orders", "filled", "buy_value", "sell_value", "last_order"]
    if trades.empty or "strategy_name" not in trades or trades["strategy_name"].isna().all():
        return pd.DataFrame(columns=columns)
    theirs = trades[trades["strategy_name"].notna()]
    done = theirs["status"] == "FILLED"
    flagged = theirs.assign(
        filled=done,
        buy_value=theirs["notional"].where(done & (theirs["side"] == "BUY"), 0),
        sell_value=theirs["notional"].where(done & (theirs["side"] == "SELL"), 0))
    summary = flagged.groupby(["strategy_name", "username", "symbol"], as_index=False).agg(
        orders=("order_id", "count"), filled=("filled", "sum"), buy_value=("buy_value", "sum"),
        sell_value=("sell_value", "sum"), last_order=("created_on", "max"))
    return summary.sort_values(["orders", "strategy_name"], ascending=[False, True]).reset_index(drop=True)[columns]


# Rejection reasons are free text with symbols, accounts and amounts in them
# ("Cannot sell 20 of MSFT, only 0 available"), so they are grouped by keyword.
REJECTION_CATEGORIES = [
    ("Insufficient funds", ("insufficient funds",)),
    ("Insufficient holdings", ("insufficient holdings", "no position", "cannot sell")),
    ("Account not active", ("not active", "suspended", "account not found")),
    ("Instrument not tradable", ("not tradable", "instrument not found")),
    ("No live price", ("no live price",)),
    ("System failure", ("could not be queued",)),
]


def rejection_category(reason):
    """The category a rejection reason falls in; "Other" if no keyword matches."""
    if not isinstance(reason, str) or not reason.strip():
        return "No reason recorded"
    text = reason.lower()
    for category, keywords in REJECTION_CATEGORIES:
        if any(keyword in text for keyword in keywords):
            return category
    return "Other"


def rejected_orders(trades):
    """Rejected orders with their reason's category, newest first. Needs status_reason (live source)."""
    rejected = trades[trades["status"] == "REJECTED"]
    rejected = rejected.assign(category=rejected["status_reason"].map(rejection_category))
    return rejected.sort_values("created_on", ascending=False).reset_index(drop=True)


def rejection_reasons(trades):
    """How many rejections fall in each category, with their share, biggest first."""
    rejected = rejected_orders(trades)
    if rejected.empty:
        return pd.DataFrame(columns=["category", "orders", "share"])
    reasons = rejected["category"].value_counts().rename_axis("category").reset_index(name="orders")
    reasons["share"] = reasons["orders"] / reasons["orders"].sum()
    return reasons.sort_values(["orders", "category"], ascending=[False, True]).reset_index(drop=True)


def fill_times(trades):
    """Filled orders with seconds from order to first execution (filled_on, live source), slowest first.

    Orders without an execution row, or whose execution predates the order
    (seed data), are left out.
    """
    done = filled(trades).dropna(subset=["filled_on"])
    seconds = (pd.to_datetime(done["filled_on"]) - pd.to_datetime(done["created_on"])).dt.total_seconds()
    timed = done.assign(seconds=seconds)
    timed = timed[timed["seconds"] >= 0]
    return timed.sort_values("seconds", ascending=False).reset_index(drop=True)


def fill_time_summary(trades):
    """Median and 95th percentile seconds from order to fill, or None if nothing filled yet."""
    seconds = fill_times(trades)["seconds"]
    if seconds.empty:
        return None
    return {"orders": len(seconds), "median": float(seconds.median()), "p95": float(seconds.quantile(0.95))}


STUCK_AFTER = timedelta(minutes=5)


def stuck_orders(trades, now, after=STUCK_AFTER):
    """NEW orders older than `after`: the executor should have settled them within seconds.

    now is a naive UTC timestamp, like created_on. Oldest first, with their age in minutes.
    """
    waiting = trades[(trades["status"] == "NEW") & (pd.to_datetime(trades["created_on"]) < now - after)]
    waiting = waiting.assign(minutes=(now - pd.to_datetime(waiting["created_on"])).dt.total_seconds() / 60)
    return waiting.sort_values("minutes", ascending=False).reset_index(drop=True)


def dlq_summary(dlq):
    """Dead-letter messages per failure type: total, still PENDING, and how many can be retried."""
    if dlq.empty:
        return pd.DataFrame(columns=["failure_type", "messages", "pending", "retryable"])
    flagged = dlq.assign(pending=dlq["status"] == "PENDING", retryable=dlq["is_retryable"].astype(bool))
    summary = flagged.groupby("failure_type", as_index=False).agg(
        messages=("id", "count"), pending=("pending", "sum"), retryable=("retryable", "sum"))
    return summary.sort_values(["messages", "failure_type"], ascending=[False, True]).reset_index(drop=True)
