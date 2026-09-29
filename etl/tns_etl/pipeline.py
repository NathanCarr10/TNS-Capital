"""Pipeline orchestration: Extract -> Transform -> Load with retries, error handling and a run log."""

import logging
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Optional

from tns_etl import db
from tns_etl.config import EtlConfig
from tns_etl.errors import StageError
from tns_etl.extract import ExtractResult, extract
from tns_etl.load import LoadStats, ensure_schema, load
from tns_etl.retry import call_with_retry
from tns_etl.transform import TransformResult, transform

logger = logging.getLogger(__name__)

SUCCEEDED = "SUCCEEDED"
FAILED = "FAILED"


@dataclass
class RunResult:
    run_id: str
    status: str
    started_at: datetime
    finished_at: Optional[datetime] = None
    failed_stage: Optional[str] = None
    error: Optional[str] = None
    rows_extracted: int = 0
    rows_rejected: int = 0
    load_stats: LoadStats = field(default_factory=LoadStats)

    @property
    def succeeded(self) -> bool:
        return self.status == SUCCEEDED


class PostgresRunLog:
    """Records each run in <schema>.etl_run_log.

    Best effort: if the database is down the pipeline has already failed for
    that reason, so a run-log write error is logged rather than hiding the
    original error.
    """

    def __init__(self, config: EtlConfig, connect: Callable):
        self._config = config
        self._connect = connect
        self._table = f"{config.analytics_schema}.etl_run_log"

    def start(self, result: RunResult, since: Optional[datetime], until: Optional[datetime]) -> None:
        def write(cur):
            ensure_schema(cur, self._config.analytics_schema)
            cur.execute(f"""INSERT INTO {self._table}
                            (run_id, started_at, status, window_start, window_end)
                            VALUES (%s, %s, 'RUNNING', %s, %s)""",
                        (result.run_id, result.started_at, since, until))
        self._write(write)

    def finish(self, result: RunResult) -> None:
        def write(cur):
            cur.execute(f"""UPDATE {self._table}
                            SET finished_at = %s, status = %s, failed_stage = %s,
                                error_message = %s, rows_extracted = %s,
                                rows_rejected = %s, rows_loaded = %s
                            WHERE run_id = %s""",
                        (result.finished_at, result.status, result.failed_stage,
                         result.error, result.rows_extracted, result.rows_rejected,
                         result.load_stats.fact_trades, result.run_id))
        self._write(write)

    def _write(self, fn) -> None:
        try:
            conn = self._connect(self._config)
            try:
                with conn, conn.cursor() as cur:
                    fn(cur)
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001 - must never mask the pipeline's own error
            logger.warning("Could not write to %s: %s", self._table, exc)


class EtlPipeline:
    """Runs one Extract -> Transform -> Load pass.

    Retry rules by stage:
      extract  retried on transient DB errors (read-only, safe to repeat)
      transform not retried; it is pure, so a retry would fail the same way
      load     retried on transient DB errors (single transaction + upserts,
               so a failed attempt leaves nothing behind)
    """

    def __init__(self, config: EtlConfig, connect: Callable = db.connect,
                 run_log=None, sleep: Callable[[float], None] = time.sleep):
        self.config = config
        self._connect = connect
        self._run_log = run_log if run_log is not None else PostgresRunLog(config, connect)
        self._sleep = sleep

    def run(self, since: Optional[datetime] = None, until: Optional[datetime] = None) -> RunResult:
        result = RunResult(run_id=str(uuid.uuid4()), status="RUNNING",
                           started_at=datetime.now(timezone.utc))
        logger.info("ETL run %s started (window %s -> %s)", result.run_id,
                    since or "start", until or "now")
        self._run_log.start(result, since, until)

        try:
            extracted: ExtractResult = self._stage(
                "extract", lambda: self._with_connection(lambda c: extract(c, since, until)),
                retryable=True)
            result.rows_extracted = len(extracted.orders)

            transformed: TransformResult = self._stage(
                "transform", lambda: transform(extracted), retryable=False)
            result.rows_rejected = len(transformed.rejected)

            result.load_stats = self._stage(
                "load", lambda: self._with_connection(
                    lambda c: load(c, transformed, self.config.analytics_schema)),
                retryable=True)
            result.status = SUCCEEDED
        except StageError as exc:
            result.status = FAILED
            result.failed_stage = exc.stage
            result.error = str(exc)
        finally:
            result.finished_at = datetime.now(timezone.utc)

        self._run_log.finish(result)
        elapsed = (result.finished_at - result.started_at).total_seconds()
        if result.succeeded:
            logger.info("ETL run %s SUCCEEDED in %.1fs: %d orders extracted, %d rejected, "
                        "changes %s", result.run_id, elapsed, result.rows_extracted,
                        result.rows_rejected, result.load_stats.as_dict())
        else:
            logger.error("ETL run %s FAILED in %.1fs at %s stage: %s",
                         result.run_id, elapsed, result.failed_stage, result.error)
        return result

    def _stage(self, name: str, fn: Callable, retryable: bool):
        logger.info("[%s] starting", name)
        started = time.monotonic()
        try:
            if retryable:
                value = call_with_retry(fn, policy=self.config.retry,
                                        retry_on=db.TRANSIENT_DB_ERRORS,
                                        description=f"{name} stage", sleep=self._sleep)
            else:
                value = fn()
        except Exception as exc:
            logger.exception("[%s] failed after %.2fs", name, time.monotonic() - started)
            raise StageError(name, exc) from exc
        logger.info("[%s] finished in %.2fs", name, time.monotonic() - started)
        return value

    def _with_connection(self, fn):
        # A fresh connection per attempt, so a retry never reuses a broken one.
        conn = self._connect(self.config)
        try:
            return fn(conn)
        finally:
            conn.close()
