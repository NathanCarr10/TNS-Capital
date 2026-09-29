import pytest

from tns_etl.config import RetryPolicy
from tns_etl.retry import backoff_delays, call_with_retry


class Flaky:
    """Fails with `error` for the first `failures` calls, then returns 'ok'."""

    def __init__(self, failures, error=ConnectionError("db down")):
        self.failures = failures
        self.error = error
        self.calls = 0

    def __call__(self):
        self.calls += 1
        if self.calls <= self.failures:
            raise self.error
        return "ok"


POLICY = RetryPolicy(max_attempts=3, initial_delay_seconds=1, backoff_multiplier=2)


def test_backoff_delays_grow_exponentially_and_are_capped():
    policy = RetryPolicy(max_attempts=5, initial_delay_seconds=2, backoff_multiplier=3, max_delay_seconds=10)
    assert list(backoff_delays(policy)) == [2, 6, 10, 10]


def test_returns_immediately_when_first_attempt_succeeds():
    fn, sleeps = Flaky(0), []
    assert call_with_retry(fn, policy=POLICY, retry_on=(ConnectionError,),
                           description="t", sleep=sleeps.append) == "ok"
    assert fn.calls == 1
    assert sleeps == []


def test_retries_transient_failure_with_backoff_then_succeeds():
    fn, sleeps = Flaky(2), []
    assert call_with_retry(fn, policy=POLICY, retry_on=(ConnectionError,),
                           description="t", sleep=sleeps.append) == "ok"
    assert fn.calls == 3
    assert sleeps == [1, 2]


def test_gives_up_after_max_attempts_and_reraises_last_error():
    fn, sleeps = Flaky(10), []
    with pytest.raises(ConnectionError):
        call_with_retry(fn, policy=POLICY, retry_on=(ConnectionError,),
                        description="t", sleep=sleeps.append)
    assert fn.calls == 3
    assert sleeps == [1, 2]


def test_does_not_retry_non_transient_errors():
    fn, sleeps = Flaky(1, error=ValueError("bad data")), []
    with pytest.raises(ValueError):
        call_with_retry(fn, policy=POLICY, retry_on=(ConnectionError,),
                        description="t", sleep=sleeps.append)
    assert fn.calls == 1
    assert sleeps == []


@pytest.mark.parametrize("kwargs", [
    {"max_attempts": 0},
    {"initial_delay_seconds": -1},
    {"backoff_multiplier": 0.5},
])
def test_retry_policy_rejects_invalid_settings(kwargs):
    with pytest.raises(ValueError):
        RetryPolicy(**kwargs)
