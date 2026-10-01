"""Unit tests for retries and error handling. No database needed:
the extract and load steps are replaced with fake versions."""

import psycopg2
import pytest

import pipeline


@pytest.fixture(autouse=True)
def no_waiting(monkeypatch):
    """Record retry waits instead of actually sleeping."""
    waits = []
    monkeypatch.setattr(pipeline.time, "sleep", waits.append)
    return waits


@pytest.fixture
def fake_database(monkeypatch, accounts, instruments, orders):
    """Replace the real database steps with fakes that can be told to fail."""
    calls = {"extract": 0, "load": 0}
    failures = {"extract": [], "load": []}

    def fake_extract():
        calls["extract"] += 1
        if failures["extract"]:
            raise failures["extract"].pop(0)
        return accounts, instruments, orders

    def fake_load(tables):
        calls["load"] += 1
        if failures["load"]:
            raise failures["load"].pop(0)

    monkeypatch.setattr(pipeline, "run_extract", fake_extract)
    monkeypatch.setattr(pipeline, "run_load", fake_load)
    return calls, failures


CONNECTION_ERROR = psycopg2.OperationalError("could not connect to server")


# ---------- with_retry ----------

def test_retry_succeeds_after_a_failure(no_waiting):
    attempts = []

    def flaky():
        attempts.append(1)
        if len(attempts) < 3:
            raise CONNECTION_ERROR
        return "done"

    assert pipeline.with_retry(flaky) == "done"
    assert len(attempts) == 3
    assert no_waiting == [2, 4]  # the wait doubles each time


def test_retry_gives_up_after_three_attempts(no_waiting):
    attempts = []

    def always_fails():
        attempts.append(1)
        raise CONNECTION_ERROR

    with pytest.raises(psycopg2.OperationalError):
        pipeline.with_retry(always_fails)
    assert len(attempts) == 3


def test_other_errors_are_not_retried():
    attempts = []

    def bug():
        attempts.append(1)
        raise ValueError("not a connection problem")

    with pytest.raises(ValueError):
        pipeline.with_retry(bug)
    assert len(attempts) == 1


# ---------- run_pipeline ----------

def test_pipeline_succeeds(fake_database):
    calls, _ = fake_database
    assert pipeline.run_pipeline() is True
    assert calls == {"extract": 1, "load": 1}


def test_pipeline_retries_a_dropped_connection(fake_database):
    calls, failures = fake_database
    failures["load"] = [CONNECTION_ERROR]

    assert pipeline.run_pipeline() is True
    assert calls["load"] == 2


def test_extract_failure_stops_the_pipeline(fake_database):
    calls, failures = fake_database
    failures["extract"] = [CONNECTION_ERROR] * 3

    assert pipeline.run_pipeline() is False
    assert calls["load"] == 0  # load never ran


def test_load_failure_is_reported(fake_database):
    _, failures = fake_database
    failures["load"] = [CONNECTION_ERROR] * 3

    assert pipeline.run_pipeline() is False


def test_transform_failure_is_reported(fake_database, monkeypatch):
    calls, _ = fake_database

    def broken_transform(*args):
        raise KeyError("price")

    monkeypatch.setattr(pipeline, "transform", broken_transform)

    assert pipeline.run_pipeline() is False
    assert calls["load"] == 0
