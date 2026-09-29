from datetime import datetime
from decimal import Decimal

import pandas as pd
import pytest

from tns_etl.extract import ExtractResult


def make_order(**overrides) -> dict:
    order = {
        "order_id": "11111111-1111-1111-1111-111111111111",
        "account_pk": 1,
        "symbol": "ACME",
        "side": "BUY",
        "quantity": 100,
        "price": Decimal("25.00"),
        "status": "FILLED",
        "idempotency_key": "key-1",
        "created_on": datetime(2026, 8, 1, 9, 0),
    }
    order.update(overrides)
    return order


@pytest.fixture
def accounts() -> pd.DataFrame:
    return pd.DataFrame([
        {"account_pk": 1, "account_id": "ACC-1001", "holder_name": "Alice", "status": "ACTIVE"},
        {"account_pk": 2, "account_id": "ACC-1002", "holder_name": "Bob", "status": "SUSPENDED"},
    ])


@pytest.fixture
def instruments() -> pd.DataFrame:
    return pd.DataFrame([
        {"symbol": "ACME", "name": "Acme Corp", "asset_class": "EQUITY", "currency": "USD", "tradable": True},
        {"symbol": "BOND1", "name": "Gov Bond", "asset_class": "BOND", "currency": "EUR", "tradable": False},
    ])


@pytest.fixture
def orders() -> pd.DataFrame:
    return pd.DataFrame([
        make_order(),
        make_order(order_id="22222222-2222-2222-2222-222222222222", account_pk=2,
                   symbol="BOND1", side="SELL", quantity=10, price=Decimal("41.00"),
                   status="NEW", idempotency_key="key-2",
                   created_on=datetime(2026, 8, 2, 10, 30)),
    ])


@pytest.fixture
def extracted(accounts, instruments, orders) -> ExtractResult:
    return ExtractResult(accounts=accounts, instruments=instruments, orders=orders)
