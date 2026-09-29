"""A small daily scheduler that runs the pipeline at a fixed time of day.

Kept dependency-free on purpose: the pipeline runs once a day, and a failed
run must not stop the next one from happening.
"""

import logging
import signal
import threading
from datetime import datetime, time, timedelta
from typing import Callable, Optional

logger = logging.getLogger(__name__)


def next_run_after(now: datetime, run_at: time) -> datetime:
    """The next occurrence of run_at strictly after now."""
    candidate = datetime.combine(now.date(), run_at)
    if candidate <= now:
        candidate += timedelta(days=1)
    return candidate


class DailyScheduler:
    def __init__(self, job: Callable[[], object], run_at: time,
                 now: Callable[[], datetime] = datetime.now):
        self._job = job
        self._run_at = run_at
        self._now = now
        self._stop = threading.Event()

    def stop(self, *_signal_args) -> None:
        logger.info("Scheduler stopping")
        self._stop.set()

    def install_signal_handlers(self) -> None:
        """Stop cleanly on Ctrl+C or `docker stop` instead of dying mid-run."""
        signal.signal(signal.SIGTERM, self.stop)
        signal.signal(signal.SIGINT, self.stop)

    def run_forever(self, run_immediately: bool = False, max_runs: Optional[int] = None) -> int:
        """Run the job daily until stopped. Returns the number of runs made."""
        runs = 0
        if run_immediately:
            self._run_job()
            runs += 1
        while not self._stop.is_set() and (max_runs is None or runs < max_runs):
            due = next_run_after(self._now(), self._run_at)
            logger.info("Next ETL run scheduled for %s", due.isoformat(sep=" ", timespec="minutes"))
            # Wake at least every minute so clock changes and stop requests are noticed.
            while not self._stop.is_set() and self._now() < due:
                remaining = (due - self._now()).total_seconds()
                self._stop.wait(timeout=max(0.0, min(remaining, 60.0)))
            if self._stop.is_set():
                break
            self._run_job()
            runs += 1
        return runs

    def _run_job(self) -> None:
        try:
            self._job()
        except Exception:  # noqa: BLE001 - one bad run must not kill the schedule
            logger.exception("Scheduled ETL run raised an unexpected error")
