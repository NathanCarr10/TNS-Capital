"""Unit tests for the dashboard insights: small hand-made DataFrames, no database."""

import pandas as pd
import pytest

import insights


def trade(order_id, account="ACC-1", holder="Ann", symbol="ACME", asset_class="Equity",
          side="BUY", quantity=10, price=5.0, status="FILLED", day="2026-10-01"):
    return {"order_id": order_id, "account_number": account, "holder_name": holder, "symbol": symbol,
            "asset_class": asset_class, "side": side, "quantity": quantity, "price": price,
            "notional": quantity * price, "status": status, "created_on": pd.Timestamp(day + " 10:00")}


@pytest.fixture
def trades():
    return pd.DataFrame([
        trade("o1", quantity=10, price=5.0),                                    # 50
        trade("o2", quantity=4, price=25.0, day="2026-10-02"),                  # 100
        trade("o3", account="ACC-2", holder="Bob", side="SELL", quantity=2, price=10.0,
              symbol="BOND1", asset_class="Bond", day="2026-10-02"),           # 20
        trade("o4", account="ACC-2", holder="Bob", status="REJECTED"),
        trade("o5", status="CANCELLED"),
        trade("o6", account="ACC-3", holder="Cy", status="NEW"),
    ])


def test_trade_volume_counts_only_filled_trades_per_day(trades):
    volume = insights.trade_volume_by_date(trades)

    assert volume["trades"].tolist() == [1, 2]
    assert volume["value"].tolist() == [50.0, 120.0]


def test_most_active_accounts_ranks_by_order_count(trades):
    active = insights.most_active_accounts(trades)

    assert active["account_number"].tolist() == ["ACC-1", "ACC-2", "ACC-3"]
    assert active.loc[0, "orders"] == 3
    assert active.loc[0, "filled_value"] == 150.0  # the cancelled order adds nothing


def test_most_active_accounts_respects_top(trades):
    assert len(insights.most_active_accounts(trades, top=1)) == 1


def test_fill_rate_ignores_orders_still_waiting(trades):
    # 3 filled out of 5 finished; the NEW order is left out
    assert insights.fill_rate(trades) == pytest.approx(0.6)


def test_fill_rate_is_none_when_nothing_finished():
    waiting = pd.DataFrame([trade("o1", status="NEW")])

    assert insights.fill_rate(waiting) is None


def test_average_trade_size_uses_filled_trades(trades):
    assert insights.average_trade_size(trades) == pytest.approx(170.0 / 3)


def test_status_breakdown_counts_each_status(trades):
    breakdown = insights.status_breakdown(trades)

    assert dict(zip(breakdown["status"], breakdown["orders"])) == {
        "CANCELLED": 1, "FILLED": 3, "NEW": 1, "REJECTED": 1}


def test_exposure_by_instrument_sums_positions_and_shares():
    positions = pd.DataFrame([
        {"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 30.0},
        {"symbol": "ACME", "asset_class": "Equity", "quantity": 10, "average_cost": 30.0},
        {"symbol": "GOLD", "asset_class": "Commodity", "quantity": 5, "average_cost": 80.0},
    ])

    exposure = insights.exposure_by_instrument(positions)

    assert exposure["symbol"].tolist() == ["ACME", "GOLD"]
    assert exposure["exposure"].tolist() == [600.0, 400.0]
    assert exposure["share"].tolist() == pytest.approx([0.6, 0.4])


def test_value_by_asset_class_splits_buys_and_sells(trades):
    mix = insights.value_by_asset_class(trades)

    assert mix.to_dict("records") == [
        {"asset_class": "Bond", "side": "SELL", "value": 20.0},
        {"asset_class": "Equity", "side": "BUY", "value": 150.0},
    ]


def test_not_yet_in_batch_finds_orders_the_etl_has_not_loaded(trades):
    batch = trades[trades["order_id"].isin(["o1", "o2", "o3"])]

    gap = insights.not_yet_in_batch(trades, batch)

    assert gap["order_id"].tolist() == ["o4", "o5", "o6"]


@pytest.mark.parametrize("function", [
    insights.trade_volume_by_date, insights.most_active_accounts, insights.value_by_asset_class])
def test_tables_are_empty_without_trades(function):
    empty = pd.DataFrame(columns=list(trade("x")))

    assert function(empty).empty


def test_numbers_are_none_without_trades():
    empty = pd.DataFrame(columns=list(trade("x")))

    assert insights.fill_rate(empty) is None
    assert insights.average_trade_size(empty) is None
    assert insights.exposure_by_instrument(pd.DataFrame(
        columns=["symbol", "asset_class", "quantity", "average_cost"])).empty
