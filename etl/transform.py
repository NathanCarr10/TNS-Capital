"""TRANSFORM: clean the raw data and reshape it into reporting tables.

These functions only use pandas. They don't talk to the database, which is
what makes them easy to unit test.

Reporting tables produced:
    dim_account     one row per account
    dim_instrument  one row per instrument
    dim_date        one row per day that has trades
    fact_trades     one row per order, with extra reporting columns
"""

import logging

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

VALID_SIDES = ["BUY", "SELL"]
VALID_STATUSES = ["NEW", "FILLED", "REJECTED", "CANCELLED"]


def clean_orders(orders, instruments):
    """Tidy up the order values and remove any invalid orders."""
    df = orders.copy()

    # Make text consistent, e.g. " buy" -> "BUY"
    df["side"] = df["side"].str.strip().str.upper()
    df["status"] = df["status"].str.strip().str.upper()
    df["symbol"] = df["symbol"].str.strip().str.upper()

    # Make sure numbers and dates really are numbers and dates.
    # errors="coerce" turns anything unreadable into NaN / NaT.
    df["quantity"] = pd.to_numeric(df["quantity"], errors="coerce")
    df["price"] = pd.to_numeric(df["price"], errors="coerce")
    # Store all times in UTC
    df["created_on"] = pd.to_datetime(df["created_on"], errors="coerce", utc=True).dt.tz_localize(None)

    known_symbols = instruments["symbol"].str.upper()
    is_valid = (
        df["order_id"].notna()
        & df["account_number"].notna()
        & df["symbol"].isin(known_symbols)
        & df["side"].isin(VALID_SIDES)
        & df["status"].isin(VALID_STATUSES)
        & (df["quantity"] > 0)
        & (df["price"] > 0)
        & df["created_on"].notna()
    )

    rejected = (~is_valid).sum()
    if rejected > 0:
        logger.warning("Removed %d invalid orders", rejected)

    clean = df[is_valid].drop_duplicates(subset="order_id", keep="last")
    clean["quantity"] = clean["quantity"].astype(int)
    return clean.reset_index(drop=True)


def build_fact_trades(clean_orders):
    """Add the extra columns reports need to each order."""
    fact = clean_orders.copy()
    fact["date_key"] = fact["created_on"].dt.strftime("%Y%m%d").astype(int)  # e.g. 20260801
    fact["signed_quantity"] = np.where(fact["side"] == "BUY", fact["quantity"], -fact["quantity"])
    fact["notional"] = (fact["price"] * fact["quantity"]).round(2)
    fact["is_filled"] = fact["status"] == "FILLED"

    columns = ["order_id", "account_number", "symbol", "date_key", "side", "quantity",
               "signed_quantity", "price", "notional", "status", "is_filled", "created_on"]
    return fact[columns].sort_values("order_id").reset_index(drop=True)


def build_dim_account(accounts):
    dim = accounts.rename(columns={"status": "account_status"})
    return dim.drop_duplicates(subset="account_number").sort_values("account_number").reset_index(drop=True)


def build_dim_instrument(instruments):
    dim = instruments.rename(columns={"name": "instrument_name"})
    dim["symbol"] = dim["symbol"].str.upper()
    return dim.drop_duplicates(subset="symbol").sort_values("symbol").reset_index(drop=True)


def build_dim_date(fact_trades):
    """One row per day that appears in the trades, with calendar details."""
    days = pd.to_datetime(fact_trades["created_on"]).dt.normalize().drop_duplicates().sort_values()
    return pd.DataFrame({
        "date_key": days.dt.strftime("%Y%m%d").astype(int),
        "full_date": days.dt.date,
        "year": days.dt.year,
        "quarter": days.dt.quarter,
        "month": days.dt.month,
        "month_name": days.dt.month_name(),
        "day": days.dt.day,
        "day_name": days.dt.day_name(),
        "is_weekend": days.dt.dayofweek >= 5,  # Saturday = 5, Sunday = 6
    }).reset_index(drop=True)


def transform(accounts, instruments, orders):
    """Run every transform step and return the four reporting tables."""
    clean = clean_orders(orders, instruments)
    fact_trades = build_fact_trades(clean)

    tables = {
        "dim_account": build_dim_account(accounts),
        "dim_instrument": build_dim_instrument(instruments),
        "dim_date": build_dim_date(fact_trades),
        "fact_trades": fact_trades,
    }
    logger.info("Transformed %d orders into %d trades", len(orders), len(fact_trades))
    return tables
