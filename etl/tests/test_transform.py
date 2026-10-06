"""Unit tests for the TRANSFORM stage."""

from datetime import date, datetime

import pytest

from transform import (
    build_dim_account,
    build_dim_date,
    build_dim_instrument,
    build_fact_trades,
    clean_orders,
    find_rejected_orders,
    transform,
)


# ---------- clean_orders ----------

def test_valid_orders_are_kept(orders, instruments):
    clean = clean_orders(orders, instruments)
    assert len(clean) == 2


def test_text_is_made_consistent(orders, instruments):
    orders.loc[0, "side"] = " buy "
    orders.loc[0, "status"] = "filled"
    orders.loc[0, "symbol"] = "acme"

    clean = clean_orders(orders, instruments)

    assert clean.loc[0, "side"] == "BUY"
    assert clean.loc[0, "status"] == "FILLED"
    assert clean.loc[0, "symbol"] == "ACME"


@pytest.mark.parametrize("column, bad_value", [
    ("order_id", None),
    ("account_number", None),
    ("symbol", "UNKNOWN"),
    ("side", "HOLD"),
    ("status", "PENDING"),
    ("quantity", 0),
    ("quantity", -5),
    ("price", 0),
    ("price", "abc"),
    ("created_on", None),
])
def test_invalid_orders_are_removed(orders, instruments, column, bad_value):
    orders[column] = orders[column].astype(object)
    orders.loc[1, column] = bad_value

    clean = clean_orders(orders, instruments)

    assert clean["order_id"].tolist() == ["order-1"]


@pytest.mark.parametrize("column, bad_value, reason", [
    ("order_id", None, "missing order_id"),
    ("symbol", "UNKNOWN", "unknown symbol"),
    ("side", "HOLD", "invalid side"),
    ("quantity", -5, "quantity is not a positive number"),
    ("price", "abc", "price is not a positive number"),
    ("created_on", None, "created_on is missing or unreadable"),
])
def test_invalid_orders_are_kept_with_the_reason(orders, instruments, column, bad_value, reason):
    orders[column] = orders[column].astype(object)
    orders.loc[1, column] = bad_value

    rejected = find_rejected_orders(orders, instruments)

    assert len(rejected) == 1
    assert rejected.loc[0, "reject_reason"] == reason
    assert rejected.loc[0, "account_number"] == "ACC-1002"


def test_rejected_orders_keep_the_values_as_received(orders, instruments):
    orders["price"] = orders["price"].astype(object)
    orders.loc[1, "price"] = "abc"

    rejected = find_rejected_orders(orders, instruments)

    assert rejected.loc[0, "price"] == "abc"
    assert rejected.loc[0, "quantity"] == "10"


def test_first_broken_rule_is_reported(orders, instruments):
    orders.loc[1, "symbol"] = "UNKNOWN"
    orders.loc[1, "side"] = "HOLD"

    rejected = find_rejected_orders(orders, instruments)

    assert rejected.loc[0, "reject_reason"] == "unknown symbol"


def test_valid_orders_produce_no_rejects(orders, instruments):
    assert find_rejected_orders(orders, instruments).empty


def test_duplicate_orders_are_removed(orders, instruments):
    orders.loc[1, "order_id"] = "order-1"
    clean = clean_orders(orders, instruments)
    assert len(clean) == 1


def test_original_data_is_not_changed(orders, instruments):
    before = orders.copy()
    clean_orders(orders, instruments)
    assert orders.equals(before)


# ---------- build_fact_trades ----------

def test_fact_trades_reporting_columns(orders, instruments):
    fact = build_fact_trades(clean_orders(orders, instruments))
    buy, sell = fact.iloc[0], fact.iloc[1]

    assert buy["signed_quantity"] == 100      # BUY is positive
    assert sell["signed_quantity"] == -10     # SELL is negative
    assert buy["notional"] == 2500.00         # 25.00 * 100
    assert sell["notional"] == 410.00         # 41.00 * 10
    assert buy["is_filled"] == True           # noqa: E712
    assert sell["is_filled"] == False         # noqa: E712
    assert fact["date_key"].tolist() == [20260801, 20260803]


# ---------- dimensions ----------

def test_dim_account(accounts):
    dim = build_dim_account(accounts)
    assert list(dim.columns) == ["account_number", "holder_name", "account_status"]
    assert len(dim) == 2


def test_dim_instrument(instruments):
    dim = build_dim_instrument(instruments)
    assert list(dim.columns) == ["symbol", "instrument_name", "asset_class", "currency"]
    assert dim["symbol"].tolist() == ["ACME", "BOND1"]


def test_dim_date_has_one_row_per_trading_day(orders, instruments):
    orders.loc[1, "created_on"] = datetime(2026, 8, 1, 17, 0)  # same day as order 1
    fact = build_fact_trades(clean_orders(orders, instruments))

    dim = build_dim_date(fact)

    assert len(dim) == 1
    day = dim.iloc[0]
    assert day["date_key"] == 20260801
    assert day["full_date"] == date(2026, 8, 1)
    assert day["day_name"] == "Saturday"
    assert day["is_weekend"] == True  # noqa: E712
    assert day["quarter"] == 3


# ---------- transform (everything together) ----------

def test_transform_returns_all_tables(accounts, instruments, orders):
    tables = transform(accounts, instruments, orders)
    assert list(tables) == ["dim_account", "dim_instrument", "dim_date", "fact_trades", "etl_rejected_orders"]
    assert len(tables["fact_trades"]) == 2
    assert tables["etl_rejected_orders"].empty


def test_transform_gives_the_same_result_every_time(accounts, instruments, orders):
    first = transform(accounts, instruments, orders)
    second = transform(accounts, instruments, orders.iloc[::-1])  # same data, different order
    for name in first:
        assert first[name].equals(second[name])
