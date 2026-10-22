"""Unit tests for the dashboard insights: small hand-made DataFrames, no database."""

from datetime import timedelta

import pandas as pd
import pytest

import insights


def trade(order_id, account="ACC-1", holder="Ann", symbol="ACME", asset_class="Equity",
          side="BUY", quantity=10, price=5.0, status="FILLED", day="2026-10-01", reason=None, filled_after=None):
    created_on = pd.Timestamp(day + " 10:00")
    filled_on = None if filled_after is None else created_on + timedelta(seconds=filled_after)
    return {"order_id": order_id, "account_number": account, "holder_name": holder,
            "email": f"{holder.lower()}@example.com", "symbol": symbol,
            "asset_class": asset_class, "side": side, "quantity": quantity, "price": price,
            "notional": quantity * price, "status": status, "created_on": created_on,
            "status_reason": reason, "filled_on": filled_on}


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


def test_trend_counts_orders_and_filled_value_per_day(trades):
    days = insights.trend(trades, "D")

    assert days["period"].tolist() == [pd.Timestamp("2026-10-01"), pd.Timestamp("2026-10-02")]
    assert days["orders"].tolist() == [4, 2]
    assert days["filled"].tolist() == [1, 2]
    assert days["value"].tolist() == [50.0, 120.0]  # only filled trades count
    # 1 filled of 3 finished on the 1st (the NEW order is left out); 2 of 2 on the 2nd
    assert days["fill_rate"].tolist() == pytest.approx([1 / 3, 1.0])


def test_trend_groups_by_week_from_monday(trades):
    weeks = insights.trend(trades, "W")

    assert weeks["period"].tolist() == [pd.Timestamp("2026-09-28")]  # 1 and 2 October 2026 share a week
    assert weeks["orders"].tolist() == [6]


def test_trend_splits_strategy_and_trader_orders(trades):
    trades = trades.assign(strategy_name=["MA 20/50", None, None, None, None, None])

    days = insights.trend(trades, "D")

    assert days["strategy_orders"].tolist() == [1, 0]
    assert days["trader_orders"].tolist() == [3, 2]


def test_trend_leaves_the_fill_rate_empty_when_nothing_finished():
    days = insights.trend(pd.DataFrame([trade("o1", status="NEW")]), "D")

    assert days["fill_rate"].isna().all()


def test_in_period_keeps_only_recent_orders(trades):
    now = pd.Timestamp("2026-10-02 12:00")

    recent = insights.in_period(trades, now, timedelta(hours=6))

    assert recent["order_id"].tolist() == ["o2", "o3"]


def test_value_by_instrument_ranks_filled_value(trades):
    top = insights.value_by_instrument(trades)

    assert top.to_dict("records") == [
        {"symbol": "ACME", "asset_class": "Equity", "trades": 2, "value": 150.0},
        {"symbol": "BOND1", "asset_class": "Bond", "trades": 1, "value": 20.0},
    ]
    assert insights.value_by_instrument(trades, top=1)["symbol"].tolist() == ["ACME"]


def test_etl_reject_reasons_counts_each_rule():
    rejects = pd.DataFrame({"reject_reason": ["unknown symbol", "invalid side", "unknown symbol"]})

    assert insights.etl_reject_reasons(rejects).to_dict("records") == [
        {"reject_reason": "unknown symbol", "orders": 2}, {"reject_reason": "invalid side", "orders": 1}]
    assert insights.etl_reject_reasons(pd.DataFrame(columns=["reject_reason"])).empty


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
    insights.trend, insights.most_active_accounts, insights.value_by_asset_class, insights.value_by_instrument])
def test_tables_are_empty_without_trades(function):
    empty = pd.DataFrame(columns=list(trade("x")))

    assert function(empty).empty


def test_numbers_are_none_without_trades():
    empty = pd.DataFrame(columns=list(trade("x")))

    assert insights.fill_rate(empty) is None
    assert insights.average_trade_size(empty) is None
    assert insights.exposure_by_instrument(pd.DataFrame(
        columns=["symbol", "asset_class", "quantity", "average_cost"])).empty


def test_trades_by_user_summarises_each_users_trading(trades):
    users = insights.trades_by_user(trades).set_index("email")

    assert users.index.tolist() == ["ann@example.com", "bob@example.com", "cy@example.com"]  # busiest first
    ann, bob, cy = users.loc["ann@example.com"], users.loc["bob@example.com"], users.loc["cy@example.com"]
    assert (ann.orders, ann.filled, ann.rejected) == (3, 2, 0)
    assert ann.fill_rate == pytest.approx(2 / 3)  # the cancelled order counts as finished
    assert (ann.buy_value, ann.sell_value, ann.top_symbol) == (150, 0, "ACME")
    assert ann.last_order == pd.Timestamp("2026-10-02 10:00")
    assert (bob.filled, bob.rejected, bob.fill_rate) == (1, 1, 0.5)
    assert (bob.buy_value, bob.sell_value, bob.top_symbol) == (0, 20, "BOND1")
    assert pd.isna(cy.fill_rate)  # only a NEW order so far
    assert cy.top_symbol is None


def test_trades_by_user_top_symbol_is_the_most_value_filled():
    users = insights.trades_by_user(pd.DataFrame([
        trade("o1", symbol="ACME", quantity=1, price=10.0),
        trade("o2", symbol="ACME", quantity=1, price=10.0),
        trade("o3", symbol="BIG", quantity=1, price=15.0),
    ]))

    assert users.loc[0, "top_symbol"] == "ACME"  # 20 beats 15


def test_trades_by_user_with_no_fills_or_trades():
    no_fills = insights.trades_by_user(pd.DataFrame([trade("o1", status="REJECTED")]))
    assert no_fills.loc[0, "top_symbol"] is None
    assert no_fills.loc[0, "fill_rate"] == 0

    empty = insights.trades_by_user(pd.DataFrame(columns=list(trade("x"))))
    assert empty.empty
    assert "email" in empty.columns


@pytest.mark.parametrize("reason, category", [
    ("Insufficient funds for buy order", "Insufficient funds"),
    ("Insufficient holdings for symbol: TSLA", "Insufficient holdings"),
    ("No position for symbol: TSLA", "Insufficient holdings"),
    ("Cannot sell 20 of MSFT, only 0 available", "Insufficient holdings"),
    ("Account ACC-1003 is suspended", "Account not active"),
    ("Account not active: 7", "Account not active"),
    ("Instrument ARKK is not tradable", "Instrument not tradable"),
    ("No live price for ACME", "No live price"),
    ("Could not be queued for execution: timeout", "System failure"),
    ("NullPointerException", "Other"),
    (None, "No reason recorded"),
    ("  ", "No reason recorded"),
])
def test_rejection_category_groups_free_text_reasons(reason, category):
    assert insights.rejection_category(reason) == category


def test_rejection_reasons_counts_only_rejected_orders():
    reasons = insights.rejection_reasons(pd.DataFrame([
        trade("o1", status="REJECTED", reason="Insufficient funds for buy order"),
        trade("o2", status="REJECTED", reason="Insufficient funds for buy order", day="2026-10-02"),
        trade("o3", status="REJECTED", reason="Cannot sell 20 of MSFT, only 0 available"),
        trade("o4", status="CANCELLED", reason="Cancelled by trader"),
        trade("o5"),
    ]))

    assert reasons.to_dict("records") == [
        {"category": "Insufficient funds", "orders": 2, "share": pytest.approx(2 / 3)},
        {"category": "Insufficient holdings", "orders": 1, "share": pytest.approx(1 / 3)},
    ]


def test_rejected_orders_are_newest_first_with_their_category():
    rejected = insights.rejected_orders(pd.DataFrame([
        trade("old", status="REJECTED", reason="No live price for ACME", day="2026-10-01"),
        trade("new", status="REJECTED", reason="Insufficient funds for buy order", day="2026-10-03"),
    ]))

    assert rejected[["order_id", "category"]].values.tolist() == [
        ["new", "Insufficient funds"], ["old", "No live price"]]


def test_no_rejections():
    assert insights.rejection_reasons(pd.DataFrame([trade("o1")])).empty


def test_fill_times_skip_untimed_and_seeded_fills():
    trades = pd.DataFrame([
        trade("quick", filled_after=1.5),
        trade("slow", filled_after=12),
        trade("seeded", filled_after=-3600),            # seed execution older than its order
        trade("no-execution"),
        trade("rejected", status="REJECTED", filled_after=1),
    ])

    assert insights.fill_times(trades)["order_id"].tolist() == ["slow", "quick"]
    summary = insights.fill_time_summary(trades)
    assert summary["orders"] == 2
    assert summary["median"] == pytest.approx(6.75)
    assert summary["p95"] == pytest.approx(11.475)


def test_fill_time_summary_is_none_before_any_timed_fill():
    assert insights.fill_time_summary(pd.DataFrame([trade("o1")])) is None


def test_stuck_orders_are_new_orders_older_than_the_threshold():
    trades = pd.DataFrame([trade("waiting", status="NEW"), trade("filled"), trade("just-placed", status="NEW")])
    trades.loc[2, "created_on"] = pd.Timestamp("2026-10-01 10:28")  # the others were placed at 10:00

    stuck = insights.stuck_orders(trades, now=pd.Timestamp("2026-10-01 10:30"))

    assert stuck["order_id"].tolist() == ["waiting"]
    assert stuck.loc[0, "minutes"] == pytest.approx(30)


def test_dlq_summary_counts_pending_and_retryable_per_failure_type():
    dlq = pd.DataFrame([
        {"id": "d1", "failure_type": "NON_RETRYABLE_InsufficientFundsException", "status": "PENDING",
         "is_retryable": False},
        {"id": "d2", "failure_type": "TimeoutException", "status": "PENDING", "is_retryable": True},
        {"id": "d3", "failure_type": "TimeoutException", "status": "RESOLVED", "is_retryable": True},
    ])

    assert insights.dlq_summary(dlq).to_dict("records") == [
        {"failure_type": "TimeoutException", "messages": 2, "pending": 1, "retryable": 2},
        {"failure_type": "NON_RETRYABLE_InsufficientFundsException", "messages": 1, "pending": 1, "retryable": 0},
    ]


def test_dlq_summary_when_nothing_failed():
    empty = insights.dlq_summary(pd.DataFrame(columns=["id", "failure_type", "status", "is_retryable"]))
    assert empty.empty


def test_strategy_vs_trader_puts_strategy_orders_next_to_the_traders_own(trades):
    trades["strategy_name"] = [None, "MA 20/50", None, "MA 20/50", None, None]

    split = insights.strategy_vs_trader(trades).set_index("placed_by")

    assert (split.loc["Strategy", "orders"], split.loc["Strategy", "filled"]) == (2, 1)
    assert split.loc["Strategy", "fill_rate"] == pytest.approx(0.5)
    assert split.loc["Strategy", "traded_value"] == 100
    assert (split.loc["Trader", "orders"], split.loc["Trader", "traded_value"]) == (4, 70)


def test_strategy_trades_summarises_each_strategy(trades):
    trades["strategy_name"] = ["Fast", "Fast", "Bands", None, None, None]

    summary = insights.strategy_trades(trades)

    assert summary["strategy_name"].tolist() == ["Fast", "Bands"]  # busiest first
    fast = summary.iloc[0]
    assert (fast.email, fast.orders, fast.filled, fast.buy_value) == ("ann@example.com", 2, 2, 150)
    assert summary.iloc[1].sell_value == 20


def test_without_strategy_orders_everything_is_the_traders(trades):
    assert insights.placed_by(trades).eq("Trader").all()
    assert insights.strategy_trades(trades).empty
    assert insights.strategy_vs_trader(trades)["placed_by"].tolist() == ["Trader"]
    assert insights.strategy_vs_trader(trades.iloc[0:0]).empty
