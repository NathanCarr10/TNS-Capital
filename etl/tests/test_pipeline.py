"""Pipeline orchestration tests: stage order, error handling and retries, with no database."""

import psycopg2
import pytest

from tns_etl import pipeline as pipeline_module
from tns_etl.config import EtlConfig, RetryPolicy
from tns_etl.load import LoadStats
from tns_etl.pipeline import FAILED, SUCCEEDED, EtlPipeline


class FakeConnection:
    def __init__(self):
        self.closed = False

    def close(self):
        self.closed = True


class RecordingRunLog:
    def __init__(self):
        self.started, self.finished = [], []

    def start(self, result, since, until):
        self.started.append((result.run_id, since, until))

    def finish(self, result):
        self.finished.append(result)


@pytest.fixture
def config():
    return EtlConfig(retry=RetryPolicy(max_attempts=3, initial_delay_seconds=1))


@pytest.fixture
def run_log():
    return RecordingRunLog()


@pytest.fixture
def connections():
    return []


@pytest.fixture
def make_pipeline(config, run_log, connections):
    sleeps = []

    def connect(_config):
        conn = FakeConnection()
        connections.append(conn)
        return conn

    def factory():
        p = EtlPipeline(config, connect=connect, run_log=run_log, sleep=sleeps.append)
        p.sleeps = sleeps
        return p

    return factory


@pytest.fixture
def stages(monkeypatch, extracted):
    """Replace the real E/T/L functions with recording fakes that can be told to fail."""
    calls = []
    failures = {"extract": [], "transform": [], "load": []}

    def fake(name, value):
        def stage(*args, **kwargs):
            calls.append(name)
            if failures[name]:
                raise failures[name].pop(0)
            return value
        return stage

    from tns_etl.transform import transform
    monkeypatch.setattr(pipeline_module, "extract", fake("extract", extracted))
    monkeypatch.setattr(pipeline_module, "transform", fake("transform", transform(extracted)))
    monkeypatch.setattr(pipeline_module, "load", fake("load", LoadStats(fact_trades=2)))
    return calls, failures


def test_runs_extract_transform_load_in_order(make_pipeline, stages, run_log, connections):
    calls, _ = stages
    result = make_pipeline().run()

    assert calls == ["extract", "transform", "load"]
    assert result.status == SUCCEEDED
    assert result.rows_extracted == 2
    assert result.rows_rejected == 0
    assert result.load_stats.fact_trades == 2
    assert run_log.finished == [result]
    assert connections and all(c.closed for c in connections)


def test_transient_extract_failure_is_retried(make_pipeline, stages):
    calls, failures = stages
    failures["extract"] = [psycopg2.OperationalError("connection refused")]
    p = make_pipeline()

    result = p.run()

    assert result.status == SUCCEEDED
    assert calls == ["extract", "extract", "transform", "load"]
    assert p.sleeps == [1]


def test_transient_load_failure_is_retried(make_pipeline, stages):
    calls, failures = stages
    failures["load"] = [psycopg2.OperationalError("server closed the connection"),
                        psycopg2.InterfaceError("connection already closed")]
    p = make_pipeline()

    result = p.run()

    assert result.status == SUCCEEDED
    assert calls.count("load") == 3
    assert p.sleeps == [1, 2]


def test_stage_fails_after_retries_exhausted(make_pipeline, stages, run_log, connections):
    calls, failures = stages
    failures["load"] = [psycopg2.OperationalError("down")] * 3

    result = make_pipeline().run()

    assert result.status == FAILED
    assert result.failed_stage == "load"
    assert "OperationalError" in result.error
    assert calls.count("load") == 3
    assert run_log.finished[-1].failed_stage == "load"
    assert all(c.closed for c in connections)  # no leaked connections on failure


def test_extract_failure_stops_pipeline_before_later_stages(make_pipeline, stages):
    calls, failures = stages
    failures["extract"] = [psycopg2.errors.UndefinedTable('relation "orders" does not exist')]

    result = make_pipeline().run()

    assert result.status == FAILED
    assert result.failed_stage == "extract"
    assert calls == ["extract"]  # not a transient error, so not retried


def test_transform_failure_is_not_retried(make_pipeline, stages):
    calls, failures = stages
    failures["transform"] = [ValueError("bad data")]

    result = make_pipeline().run()

    assert result.status == FAILED
    assert result.failed_stage == "transform"
    assert calls == ["extract", "transform"]


def test_every_run_is_logged_with_its_window(make_pipeline, stages, run_log):
    from datetime import datetime
    since, until = datetime(2026, 8, 1), datetime(2026, 9, 1)

    result = make_pipeline().run(since=since, until=until)

    assert run_log.started == [(result.run_id, since, until)]
    assert result.finished_at >= result.started_at


def test_run_log_failure_does_not_mask_pipeline_result(config, stages):
    def broken_connect(_config):
        raise psycopg2.OperationalError("run log db unavailable")

    from tns_etl.pipeline import PostgresRunLog
    run_log = PostgresRunLog(config, broken_connect)
    p = EtlPipeline(config, connect=lambda _c: FakeConnection(), run_log=run_log, sleep=lambda _s: None)

    assert p.run().status == SUCCEEDED
