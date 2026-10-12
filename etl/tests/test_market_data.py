"""Unit tests for the Alpaca client. No network: HTTP calls are replaced with fakes."""

import io
import json
import urllib.error
from datetime import date, datetime, timezone
from urllib.parse import parse_qs, urlparse

import pytest

import market_data

NOW = datetime(2026, 10, 12, 2, 0, tzinfo=timezone.utc)


@pytest.fixture(autouse=True)
def alpaca_keys(monkeypatch):
    monkeypatch.setattr(market_data.config, "ALPACA_API_KEY_ID", "my-key")
    monkeypatch.setattr(market_data.config, "ALPACA_API_SECRET_KEY", "my-secret")
    monkeypatch.setattr(market_data.time, "sleep", lambda seconds: None)


@pytest.fixture
def fake_alpaca(monkeypatch):
    """Answer requests with the queued responses (a dict is a JSON body, an int an HTTP error)."""
    responses = []
    requests = []

    def fake_urlopen(request, timeout):
        requests.append(request)
        response = responses.pop(0)
        if isinstance(response, int):
            raise urllib.error.HTTPError(request.full_url, response, "error", {}, io.BytesIO(b"{}"))
        return io.BytesIO(json.dumps(response).encode())

    monkeypatch.setattr(market_data.urllib.request, "urlopen", fake_urlopen)
    return responses, requests


def query(request):
    return {key: values[0] for key, values in parse_qs(urlparse(request.full_url).query).items()}


# ---------- start_dates ----------

def test_new_symbols_start_at_the_backfill_date_and_others_at_their_last_day():
    groups = market_data.start_dates(
        {"AAPL": date(2026, 10, 9), "MSFT": date(2026, 10, 9), "NVDA": None},
        today=date(2026, 10, 12), backfill_years=1)

    assert groups == {date(2026, 10, 9): ["AAPL", "MSFT"], date(2025, 10, 12): ["NVDA"]}


# ---------- fetch_daily_bars ----------

def test_sends_keys_and_asks_for_daily_bars_up_to_16_minutes_ago(fake_alpaca):
    responses, requests = fake_alpaca
    responses.append({"bars": {"AAPL": [{"t": "2026-10-09T04:00:00Z", "c": 227.3}]}, "next_page_token": None})

    bars = market_data.fetch_daily_bars(["AAPL", "MSFT"], date(2026, 10, 1), now=NOW)

    assert bars == {"AAPL": [{"t": "2026-10-09T04:00:00Z", "c": 227.3}]}
    assert requests[0].get_header("Apca-api-key-id") == "my-key"
    assert requests[0].get_header("Apca-api-secret-key") == "my-secret"
    params = query(requests[0])
    assert params["symbols"] == "AAPL,MSFT"
    assert params["timeframe"] == "1Day"
    assert params["start"] == "2026-10-01"
    assert params["end"] == "2026-10-12T01:44:00Z"


def test_follows_page_tokens(fake_alpaca):
    responses, requests = fake_alpaca
    responses.append({"bars": {"AAPL": [{"t": "day-1"}]}, "next_page_token": "page-2"})
    responses.append({"bars": {"AAPL": [{"t": "day-2"}], "MSFT": [{"t": "day-1"}]}, "next_page_token": None})

    bars = market_data.fetch_daily_bars(["AAPL", "MSFT"], date(2026, 10, 1), now=NOW)

    assert bars == {"AAPL": [{"t": "day-1"}, {"t": "day-2"}], "MSFT": [{"t": "day-1"}]}
    assert "page_token" not in query(requests[0])
    assert query(requests[1])["page_token"] == "page-2"


def test_retries_when_rate_limited(fake_alpaca):
    responses, requests = fake_alpaca
    responses.extend([429, {"bars": {}, "next_page_token": None}])

    assert market_data.fetch_daily_bars(["AAPL"], date(2026, 10, 1), now=NOW) == {}
    assert len(requests) == 2


def test_gives_up_after_three_attempts(fake_alpaca):
    responses, requests = fake_alpaca
    responses.extend([503, 503, 503])

    with pytest.raises(market_data.MarketDataError):
        market_data.fetch_daily_bars(["AAPL"], date(2026, 10, 1), now=NOW)
    assert len(requests) == 3


def test_wrong_keys_are_not_retried(fake_alpaca):
    responses, requests = fake_alpaca
    responses.append(403)

    with pytest.raises(market_data.MarketDataError, match="403"):
        market_data.fetch_daily_bars(["AAPL"], date(2026, 10, 1), now=NOW)
    assert len(requests) == 1
