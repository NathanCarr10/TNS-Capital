"""Small sample tables shared by the tests (they look like what extract() returns)."""

from datetime import datetime
from decimal import Decimal

import pandas as pd
import pytest


@pytest.fixture
def accounts():
    return pd.DataFrame({
        "account_id": ["ACC-1001", "ACC-1002"],
        "holder_name": ["Alice", "Bob"],
        "status": ["ACTIVE", "SUSPENDED"],
    })


@pytest.fixture
def instruments():
    return pd.DataFrame({
        "symbol": ["ACME", "BOND1"],
        "name": ["Acme Corp", "Gov Bond"],
        "asset_class": ["EQUITY", "BOND"],
        "currency": ["USD", "EUR"],
    })


@pytest.fixture
def orders():
    return pd.DataFrame({
        "order_id": ["order-1", "order-2"],
        "account_id": ["ACC-1001", "ACC-1002"],
        "symbol": ["ACME", "BOND1"],
        "side": ["BUY", "SELL"],
        "quantity": [100, 10],
        "price": [Decimal("25.00"), Decimal("41.00")],  # Postgres returns prices as Decimal
        "status": ["FILLED", "NEW"],
        "created_on": [datetime(2026, 8, 1, 9, 0), datetime(2026, 8, 3, 10, 30)],
    })
