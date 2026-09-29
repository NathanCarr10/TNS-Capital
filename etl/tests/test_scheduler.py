from datetime import datetime, time, timedelta

import pytest

from tns_etl.config import EtlConfig, parse_schedule_time
from tns_etl.scheduler import DailyScheduler, next_run_after


@pytest.mark.parametrize("now, expected", [
    (datetime(2026, 9, 29, 1, 0), datetime(2026, 9, 29, 2, 0)),    # later today
    (datetime(2026, 9, 29, 2, 0), datetime(2026, 9, 30, 2, 0)),    # exactly now -> tomorrow
    (datetime(2026, 9, 29, 23, 0), datetime(2026, 9, 30, 2, 0)),   # already passed
    (datetime(2026, 12, 31, 3, 0), datetime(2027, 1, 1, 2, 0)),    # year rollover
])
def test_next_run_after(now, expected):
    assert next_run_after(now, time(2, 0)) == expected


class FakeClock:
    """A clock that jumps forward whenever the scheduler waits."""

    def __init__(self, start):
        self.current = start

    def __call__(self):
        return self.current


def make_scheduler(job, clock):
    scheduler = DailyScheduler(job, run_at=time(2, 0), now=clock)
    # Replace the real wait with an instant clock advance.
    scheduler._stop.wait = lambda timeout: setattr(clock, "current", clock.current + timedelta(seconds=timeout))
    return scheduler


def test_runs_job_once_per_day_at_scheduled_time():
    clock = FakeClock(datetime(2026, 9, 29, 12, 0))
    run_times = []
    scheduler = make_scheduler(lambda: run_times.append(clock()), clock)

    assert scheduler.run_forever(max_runs=2) == 2
    assert run_times == [datetime(2026, 9, 30, 2, 0), datetime(2026, 10, 1, 2, 0)]


def test_run_immediately_then_waits_for_schedule():
    clock = FakeClock(datetime(2026, 9, 29, 12, 0))
    run_times = []
    scheduler = make_scheduler(lambda: run_times.append(clock()), clock)

    scheduler.run_forever(run_immediately=True, max_runs=2)
    assert run_times == [datetime(2026, 9, 29, 12, 0), datetime(2026, 9, 30, 2, 0)]


def test_a_failing_run_does_not_stop_the_schedule():
    clock = FakeClock(datetime(2026, 9, 29, 12, 0))
    attempts = []

    def job():
        attempts.append(clock())
        raise RuntimeError("boom")

    assert make_scheduler(job, clock).run_forever(max_runs=3) == 3
    assert len(attempts) == 3


def test_stop_ends_the_loop():
    clock = FakeClock(datetime(2026, 9, 29, 12, 0))
    scheduler = make_scheduler(lambda: None, clock)
    scheduler.stop()
    assert scheduler.run_forever() == 0


def test_parse_schedule_time():
    assert parse_schedule_time("02:30") == time(2, 30)
    with pytest.raises(ValueError):
        parse_schedule_time("2am")


def test_config_from_env():
    config = EtlConfig.from_env({"ETL_DB_HOST": "postgres", "DB_PASSWORD": "pw",
                                 "ETL_SCHEDULE_TIME": "03:15", "ETL_RETRY_MAX_ATTEMPTS": "5"})
    assert config.db_host == "postgres"
    assert config.db_password == "pw"
    assert config.schedule_time == time(3, 15)
    assert config.retry.max_attempts == 5


def test_config_rejects_unsafe_schema_name():
    with pytest.raises(ValueError):
        EtlConfig(analytics_schema="analytics; DROP TABLE orders")
