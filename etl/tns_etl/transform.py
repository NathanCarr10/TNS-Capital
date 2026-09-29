"""Transform stage: turn raw operational tables into a reporting star schema.

Every function here is pure (DataFrames in, DataFrames out, no I/O and no
clock reads), so the same input always produces the same output. That is what
makes pipeline runs repeatable and what the unit tests rely on.

Output tables:
    dim_account     one row per trading account
    dim_instrument  one row per instrument
    dim_date        one row per calendar day that has trades
    fact_trades     one row per order, with derived reporting measures
"""

import logging
from dataclasses import dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from typing import Iterable, Optional, Tuple

import pandas as pd

from tns_etl.errors import SchemaError
from tns_etl.extract import ExtractResult

logger = logging.getLogger(__name__)

VALID_SIDES = frozenset({"BUY", "SELL"})
VALID_STATUSES = frozenset({"NEW", "FILLED", "REJECTED", "CANCELLED"})
CENTS = Decimal("0.01")

ACCOUNT_COLUMNS = ("account_pk", "account_id", "holder_name", "status")
INSTRUMENT_COLUMNS = ("symbol", "name", "asset_class", "currency", "tradable")
ORDER_COLUMNS = ("order_id", "account_pk", "symbol", "side", "quantity",
                 "price", "status", "idempotency_key", "created_on")

FACT_COLUMNS = ["order_id", "account_id", "symbol", "date_key", "side", "quantity",
                "signed_quantity", "price", "notional", "status", "is_filled",
                "created_on", "idempotency_key"]


@dataclass
class TransformResult:
    dim_account: pd.DataFrame
    dim_instrument: pd.DataFrame
    dim_date: pd.DataFrame
    fact_trades: pd.DataFrame
    rejected: pd.DataFrame


def transform(extracted: ExtractResult) -> TransformResult:
    dim_account = build_dim_account(extracted.accounts)
    dim_instrument = build_dim_instrument(extracted.instruments)
    clean, rejected = clean_orders(extracted.orders, extracted.accounts, extracted.instruments)
    fact_trades = build_fact_trades(clean)
    dim_date = build_dim_date(fact_trades["created_on"])

    if len(rejected):
        logger.warning("Rejected %d of %d orders: %s", len(rejected), len(extracted.orders),
                       rejected["reject_reason"].value_counts().to_dict())
    logger.info("Transformed %d accounts, %d instruments, %d dates, %d trades",
                len(dim_account), len(dim_instrument), len(dim_date), len(fact_trades))
    return TransformResult(dim_account, dim_instrument, dim_date, fact_trades, rejected)


# --------------------------------------------------------------------------- helpers

def require_columns(df: pd.DataFrame, columns: Iterable[str], table: str) -> None:
    missing = [c for c in columns if c not in df.columns]
    if missing:
        raise SchemaError(f"{table} is missing required columns: {missing}")


def normalise_code(series: pd.Series) -> pd.Series:
    """Trim and upper-case codes such as side, status and symbol ('  buy ' -> 'BUY')."""
    return series.astype("string").str.strip().str.upper()


def to_money(value) -> Optional[Decimal]:
    """Convert a price-like value to a Decimal rounded to cents, or None if unusable.

    Goes through str() so floats like 0.1 don't carry binary noise into Decimal.
    """
    if value is None or (not isinstance(value, str) and pd.isna(value)):
        return None
    try:
        return Decimal(str(value).strip()).quantize(CENTS, rounding=ROUND_HALF_UP)
    except (InvalidOperation, ValueError):
        return None


def to_date_key(day: date) -> int:
    """20260801-style integer key for a calendar day."""
    return day.year * 10000 + day.month * 100 + day.day


def to_utc_naive(series: pd.Series) -> pd.Series:
    """Parse timestamps and express them as naive UTC.

    The source column is TIMESTAMP in db/tables but Hibernate maps Instant to
    TIMESTAMPTZ; converting both to naive UTC gives one consistent type.
    """
    return pd.to_datetime(series, errors="coerce", utc=True).dt.tz_localize(None)


# --------------------------------------------------------------------------- dimensions

def build_dim_account(accounts: pd.DataFrame) -> pd.DataFrame:
    require_columns(accounts, ACCOUNT_COLUMNS, "accounts")
    df = pd.DataFrame({
        "account_id": accounts["account_id"].astype("string").str.strip(),
        "holder_name": accounts["holder_name"].astype("string").str.strip(),
        "account_status": normalise_code(accounts["status"]),
    })
    df = df.dropna(subset=["account_id"])
    return (df.drop_duplicates(subset="account_id", keep="last")
              .sort_values("account_id")
              .reset_index(drop=True))


def build_dim_instrument(instruments: pd.DataFrame) -> pd.DataFrame:
    require_columns(instruments, INSTRUMENT_COLUMNS, "instruments")
    df = pd.DataFrame({
        "symbol": normalise_code(instruments["symbol"]),
        "instrument_name": instruments["name"].astype("string").str.strip(),
        "asset_class": instruments["asset_class"].astype("string").str.strip(),
        "currency": normalise_code(instruments["currency"]),
        "tradable": instruments["tradable"].fillna(False).astype(bool),
    })
    df = df.dropna(subset=["symbol"])
    return (df.drop_duplicates(subset="symbol", keep="last")
              .sort_values("symbol")
              .reset_index(drop=True))


def build_dim_date(timestamps: pd.Series) -> pd.DataFrame:
    """Calendar attributes for every distinct day that appears in the trades."""
    days = sorted({ts.date() for ts in pd.to_datetime(timestamps).dropna()})
    index = pd.DatetimeIndex(days)
    return pd.DataFrame({
        "date_key": [to_date_key(d) for d in days],
        "full_date": days,
        "year": index.year,
        "quarter": index.quarter,
        "month": index.month,
        "month_name": index.month_name(),
        "day": index.day,
        "day_of_week": index.dayofweek + 1,  # ISO: Monday = 1
        "day_name": index.day_name(),
        "is_weekend": index.dayofweek >= 5,
    }).astype({"year": int, "quarter": int, "month": int, "day": int, "day_of_week": int})


# --------------------------------------------------------------------------- facts

def clean_orders(orders: pd.DataFrame, accounts: pd.DataFrame,
                 instruments: pd.DataFrame) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """Normalise orders and split them into (clean, rejected).

    Rejected rows keep their original values plus a reject_reason, so bad data
    is reported rather than silently dropped or allowed to break the load.
    """
    require_columns(orders, ORDER_COLUMNS, "orders")
    require_columns(accounts, ("account_pk", "account_id"), "accounts")
    require_columns(instruments, ("symbol",), "instruments")

    df = orders.copy()
    df["order_id"] = df["order_id"].astype("string").str.strip()
    df["idempotency_key"] = df["idempotency_key"].astype("string").str.strip()
    df["side"] = normalise_code(df["side"])
    df["status"] = normalise_code(df["status"])
    df["symbol"] = normalise_code(df["symbol"])
    df["quantity"] = pd.to_numeric(df["quantity"], errors="coerce")
    df["price"] = df["price"].map(to_money)
    df["created_on"] = to_utc_naive(df["created_on"])

    # Orders reference the account surrogate key; reporting uses the business ID.
    account_lookup = accounts.drop_duplicates("account_pk").set_index("account_pk")["account_id"]
    df["account_id"] = df["account_pk"].map(account_lookup).astype("string").str.strip()
    known_symbols = set(normalise_code(instruments["symbol"]).dropna())

    # Checked in order; a row is labelled with the first rule it breaks.
    rules = [
        ("missing order_id", df["order_id"].isna() | (df["order_id"] == "")),
        ("missing idempotency_key", df["idempotency_key"].isna() | (df["idempotency_key"] == "")),
        ("unknown account", df["account_id"].isna()),
        ("unknown symbol", ~df["symbol"].isin(known_symbols)),
        ("invalid side", ~df["side"].isin(VALID_SIDES)),
        ("invalid status", ~df["status"].isin(VALID_STATUSES)),
        ("invalid quantity", df["quantity"].isna() | (df["quantity"] <= 0)
                             | (df["quantity"] % 1 != 0)),
        ("invalid price", df["price"].map(lambda p: p is None or p <= 0).astype(bool)),
        ("invalid created_on", df["created_on"].isna()),
    ]
    reason = pd.Series(pd.NA, index=df.index, dtype="string")
    for label, mask in rules:
        reason = reason.mask(reason.isna() & mask.fillna(True).astype(bool), label)

    rejected = orders.loc[reason.notna()].copy()
    rejected["reject_reason"] = reason[reason.notna()]

    clean = df.loc[reason.isna()].copy()
    clean["quantity"] = clean["quantity"].astype(int)
    # The source enforces unique IDs, but if a duplicate slips through keep the
    # latest version so the fact table has exactly one row per order.
    clean = (clean.sort_values(["created_on", "order_id"])
                  .drop_duplicates(subset="order_id", keep="last")
                  .reset_index(drop=True))
    return clean, rejected.reset_index(drop=True)


def build_fact_trades(clean: pd.DataFrame) -> pd.DataFrame:
    """Add reporting measures to cleaned orders.

    signed_quantity is +qty for BUY and -qty for SELL so net positions can be
    summed; notional is price * quantity in the instrument currency.
    """
    if clean.empty:
        return pd.DataFrame(columns=FACT_COLUMNS)

    fact = clean.copy()
    fact["date_key"] = fact["created_on"].map(lambda ts: to_date_key(ts.date()))
    fact["signed_quantity"] = fact["quantity"].where(fact["side"] == "BUY", -fact["quantity"])
    fact["notional"] = [(price * qty).quantize(CENTS)
                        for price, qty in zip(fact["price"], fact["quantity"])]
    fact["is_filled"] = fact["status"] == "FILLED"
    return fact[FACT_COLUMNS].sort_values(["created_on", "order_id"]).reset_index(drop=True)
