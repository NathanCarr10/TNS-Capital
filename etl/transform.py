"""TRANSFORM: clean the raw data and reshape it into reporting tables.

These functions only use pandas. They don't talk to the database, which is
what makes them easy to unit test.

Reporting tables produced:
    dim_account     one row per account
    dim_instrument  one row per instrument
    dim_date        one row per day that has trades
    fact_trades     one row per order, with extra reporting columns
    etl_rejected_orders  orders that failed validation, with the reason
"""

import logging

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

VALID_SIDES = ["BUY", "SELL"]
VALID_STATUSES = ["NEW", "FILLED", "REJECTED", "CANCELLED"]


REJECT_COLUMNS = ["order_id", "account_number", "symbol", "side", "quantity",
                  "price", "status", "created_on", "reject_reason"]


def _prepare_orders(orders):
    """Make text consistent and turn numbers and dates into real numbers and dates."""
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
    return df


def _quality_checks(df, instruments):
    """Each data quality rule, as reason -> True for the rows that pass it."""
    known_symbols = instruments["symbol"].str.upper()
    return {
        "missing order_id": df["order_id"].notna(),
        "missing account_number": df["account_number"].notna(),
        "unknown symbol": df["symbol"].isin(known_symbols),
        "invalid side": df["side"].isin(VALID_SIDES),
        "invalid status": df["status"].isin(VALID_STATUSES),
        "quantity is not a positive number": df["quantity"] > 0,
        "price is not a positive number": df["price"] > 0,
        "created_on is missing or unreadable": df["created_on"].notna(),
    }


def clean_orders(orders, instruments):
    """Tidy up the order values and remove any invalid orders."""
    df = _prepare_orders(orders)

    is_valid = pd.Series(True, index=df.index)
    for passes in _quality_checks(df, instruments).values():
        is_valid &= passes

    rejected = (~is_valid).sum()
    if rejected > 0:
        logger.warning("Removed %d invalid orders", rejected)

    clean = df[is_valid].drop_duplicates(subset="order_id", keep="last")
    clean["quantity"] = clean["quantity"].astype(int)
    return clean.reset_index(drop=True)


def find_rejected_orders(orders, instruments):
    """The orders clean_orders removes, as received, with the first rule each one broke.

    This is the pipeline's dead-letter table: bad rows are kept for someone to
    look at instead of disappearing. Values are kept as text because they are,
    by definition, not valid numbers or dates.
    """
    df = _prepare_orders(orders)

    reason = pd.Series(None, index=df.index, dtype=object)
    # Go through the rules last to first so the first rule a row breaks wins
    for text, passes in reversed(list(_quality_checks(df, instruments).items())):
        reason[~passes] = text

    failed = reason.notna()
    rejected = orders.loc[failed, REJECT_COLUMNS[:-1]].astype(object)
    rejected = rejected.where(rejected.notna(), None).map(lambda value: None if value is None else str(value))
    rejected["reject_reason"] = reason[failed]
    return rejected.reset_index(drop=True)


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
        "etl_rejected_orders": find_rejected_orders(orders, instruments),
    }
    logger.info("Transformed %d orders into %d trades", len(orders), len(fact_trades))
    return tables
