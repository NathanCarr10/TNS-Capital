from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

import pandas as pd
import pytest

from tests.conftest import make_order
from tns_etl.errors import SchemaError
from tns_etl.transform import (
    FACT_COLUMNS,
    build_dim_account,
    build_dim_date,
    build_dim_instrument,
    build_fact_trades,
    clean_orders,
    normalise_code,
    to_date_key,
    to_money,
    transform,
)


# ----------------------------------------------------------------- small helpers

@pytest.mark.parametrize("raw, expected", [
    (Decimal("25"), Decimal("25.00")),
    (25.005, Decimal("25.01")),        # half-up, and no float noise
    (0.1, Decimal("0.10")),
    ("  12.3 ", Decimal("12.30")),
    (10, Decimal("10.00")),
])
def test_to_money_rounds_to_cents(raw, expected):
    assert to_money(raw) == expected


@pytest.mark.parametrize("raw", [None, float("nan"), pd.NA, "abc", ""])
def test_to_money_returns_none_for_unusable_values(raw):
    assert to_money(raw) is None


def test_to_date_key():
    assert to_date_key(date(2026, 8, 1)) == 20260801
    assert to_date_key(date(2026, 12, 31)) == 20261231


def test_normalise_code_trims_and_uppercases():
    result = normalise_code(pd.Series([" buy", "Sell ", None]))
    assert result.tolist()[:2] == ["BUY", "SELL"]
    assert pd.isna(result.iloc[2])


# ----------------------------------------------------------------- dimensions

def test_dim_account_maps_columns_and_sorts(accounts):
    dim = build_dim_account(accounts.iloc[::-1])
    assert list(dim.columns) == ["account_id", "holder_name", "account_status"]
    assert dim["account_id"].tolist() == ["ACC-1001", "ACC-1002"]
    assert dim.loc[1, "account_status"] == "SUSPENDED"


def test_dim_account_deduplicates_on_business_key(accounts):
    renamed = accounts.copy()
    renamed.loc[len(renamed)] = {"account_pk": 3, "account_id": "ACC-1001",
                                 "holder_name": "Alice Smith", "status": "active"}
    dim = build_dim_account(renamed)
    assert len(dim) == 2
    alice = dim.set_index("account_id").loc["ACC-1001"]
    assert alice["holder_name"] == "Alice Smith"
    assert alice["account_status"] == "ACTIVE"


def test_dim_instrument_normalises_codes(instruments):
    messy = instruments.copy()
    messy.loc[0, "symbol"] = " acme "
    messy.loc[0, "currency"] = "usd"
    dim = build_dim_instrument(messy)
    assert dim["symbol"].tolist() == ["ACME", "BOND1"]
    assert dim.loc[0, "currency"] == "USD"
    assert dim["tradable"].tolist() == [True, False]


def test_dim_date_has_calendar_attributes_for_distinct_days():
    timestamps = pd.Series([datetime(2026, 8, 1, 9), datetime(2026, 8, 1, 17),
                            datetime(2026, 8, 3, 10)])
    dim = build_dim_date(timestamps)
    assert dim["date_key"].tolist() == [20260801, 20260803]
    saturday = dim.iloc[0]
    assert saturday["full_date"] == date(2026, 8, 1)
    assert (saturday["year"], saturday["quarter"], saturday["month"], saturday["day"]) == (2026, 3, 8, 1)
    assert saturday["day_name"] == "Saturday"
    assert saturday["day_of_week"] == 6
    assert bool(saturday["is_weekend"]) is True
    assert bool(dim.iloc[1]["is_weekend"]) is False


def test_dim_date_empty_input():
    assert build_dim_date(pd.Series([], dtype="datetime64[ns]")).empty


# ----------------------------------------------------------------- cleaning / validation

def test_clean_orders_accepts_valid_orders(orders, accounts, instruments):
    clean, rejected = clean_orders(orders, accounts, instruments)
    assert len(clean) == 2
    assert rejected.empty
    assert clean["account_id"].tolist() == ["ACC-1001", "ACC-1002"]


def test_clean_orders_normalises_values(accounts, instruments):
    orders = pd.DataFrame([make_order(side=" buy ", status="filled", symbol="acme",
                                      price="25.5", quantity="100")])
    clean, rejected = clean_orders(orders, accounts, instruments)
    assert rejected.empty
    row = clean.iloc[0]
    assert (row["side"], row["status"], row["symbol"]) == ("BUY", "FILLED", "ACME")
    assert row["price"] == Decimal("25.50")
    assert row["quantity"] == 100


@pytest.mark.parametrize("overrides, reason", [
    ({"order_id": None}, "missing order_id"),
    ({"idempotency_key": ""}, "missing idempotency_key"),
    ({"account_pk": 999}, "unknown account"),
    ({"symbol": "NOPE"}, "unknown symbol"),
    ({"side": "HOLD"}, "invalid side"),
    ({"status": "PENDING"}, "invalid status"),
    ({"quantity": 0}, "invalid quantity"),
    ({"quantity": -5}, "invalid quantity"),
    ({"quantity": 1.5}, "invalid quantity"),
    ({"quantity": None}, "invalid quantity"),
    ({"price": Decimal("0")}, "invalid price"),
    ({"price": None}, "invalid price"),
    ({"created_on": None}, "invalid created_on"),
])
def test_clean_orders_rejects_invalid_rows_with_reason(accounts, instruments, overrides, reason):
    bad = make_order(**{"order_id": "bad", "idempotency_key": "bad", **overrides})
    orders = pd.DataFrame([make_order(), bad])

    clean, rejected = clean_orders(orders, accounts, instruments)

    assert len(clean) == 1
    assert rejected["reject_reason"].tolist() == [reason]


def test_clean_orders_labels_first_broken_rule(accounts, instruments):
    orders = pd.DataFrame([make_order(symbol="NOPE", side="HOLD", quantity=-1)])
    _, rejected = clean_orders(orders, accounts, instruments)
    assert rejected["reject_reason"].tolist() == ["unknown symbol"]


def test_clean_orders_keeps_latest_duplicate(accounts, instruments):
    orders = pd.DataFrame([
        make_order(status="NEW", created_on=datetime(2026, 8, 1, 9)),
        make_order(status="FILLED", created_on=datetime(2026, 8, 1, 9, 5)),
    ])
    clean, _ = clean_orders(orders, accounts, instruments)
    assert clean["status"].tolist() == ["FILLED"]


def test_clean_orders_converts_timezone_aware_timestamps_to_utc(accounts, instruments):
    dublin_summer = timezone(timedelta(hours=1))
    orders = pd.DataFrame([make_order(created_on=datetime(2026, 8, 1, 0, 30, tzinfo=dublin_summer))])
    clean, _ = clean_orders(orders, accounts, instruments)
    assert clean.loc[0, "created_on"] == pd.Timestamp("2026-07-31 23:30:00")


def test_clean_orders_missing_column_raises_schema_error(orders, accounts, instruments):
    with pytest.raises(SchemaError, match="price"):
        clean_orders(orders.drop(columns=["price"]), accounts, instruments)


def test_clean_orders_does_not_mutate_input(orders, accounts, instruments):
    before = orders.copy()
    clean_orders(orders, accounts, instruments)
    pd.testing.assert_frame_equal(orders, before)


# ----------------------------------------------------------------- facts

def test_fact_trades_derived_measures(orders, accounts, instruments):
    clean, _ = clean_orders(orders, accounts, instruments)
    fact = build_fact_trades(clean)

    assert list(fact.columns) == FACT_COLUMNS
    buy, sell = fact.iloc[0], fact.iloc[1]
    assert buy["signed_quantity"] == 100
    assert sell["signed_quantity"] == -10
    assert buy["notional"] == Decimal("2500.00")
    assert sell["notional"] == Decimal("410.00")
    assert bool(buy["is_filled"]) is True
    assert bool(sell["is_filled"]) is False
    assert fact["date_key"].tolist() == [20260801, 20260802]


def test_fact_trades_empty_input_keeps_columns():
    fact = build_fact_trades(pd.DataFrame())
    assert fact.empty
    assert list(fact.columns) == FACT_COLUMNS


# ----------------------------------------------------------------- whole transform

def test_transform_builds_star_schema(extracted):
    result = transform(extracted)
    assert len(result.dim_account) == 2
    assert len(result.dim_instrument) == 2
    assert result.dim_date["date_key"].tolist() == [20260801, 20260802]
    assert len(result.fact_trades) == 2
    assert result.rejected.empty


def test_transform_every_fact_references_a_dimension_row(extracted):
    result = transform(extracted)
    assert set(result.fact_trades["account_id"]) <= set(result.dim_account["account_id"])
    assert set(result.fact_trades["symbol"]) <= set(result.dim_instrument["symbol"])
    assert set(result.fact_trades["date_key"]) <= set(result.dim_date["date_key"])


def test_transform_is_deterministic_regardless_of_input_order(extracted):
    first = transform(extracted)
    shuffled = type(extracted)(
        accounts=extracted.accounts.iloc[::-1].reset_index(drop=True),
        instruments=extracted.instruments.iloc[::-1].reset_index(drop=True),
        orders=extracted.orders.iloc[::-1].reset_index(drop=True),
    )
    second = transform(shuffled)
    for table in ("dim_account", "dim_instrument", "dim_date", "fact_trades"):
        pd.testing.assert_frame_equal(getattr(first, table), getattr(second, table))
