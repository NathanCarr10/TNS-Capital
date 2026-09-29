"""Retry with exponential backoff."""

import logging
import time
from typing import Callable, Tuple, Type, TypeVar

from tns_etl.config import RetryPolicy

logger = logging.getLogger(__name__)

T = TypeVar("T")


def backoff_delays(policy: RetryPolicy):
    """Yield the wait before each retry: initial, initial*m, initial*m^2, ... capped at max."""
    delay = policy.initial_delay_seconds
    for _ in range(policy.max_attempts - 1):
        yield min(delay, policy.max_delay_seconds)
        delay *= policy.backoff_multiplier


def call_with_retry(
    fn: Callable[[], T],
    *,
    policy: RetryPolicy,
    retry_on: Tuple[Type[BaseException], ...],
    description: str,
    sleep: Callable[[float], None] = time.sleep,
) -> T:
    """Call fn, retrying on the given exception types with exponential backoff.

    Exceptions not listed in retry_on are raised immediately; the last
    retryable exception is re-raised once max_attempts is reached.
    """
    delays = backoff_delays(policy)
    attempt = 1
    while True:
        try:
            return fn()
        except retry_on as exc:
            delay = next(delays, None)
            if delay is None:
                logger.error("%s failed on attempt %d/%d, giving up: %s",
                             description, attempt, policy.max_attempts, exc)
                raise
            logger.warning("%s failed on attempt %d/%d: %s. Retrying in %.1fs",
                           description, attempt, policy.max_attempts, exc, delay)
            sleep(delay)
            attempt += 1
