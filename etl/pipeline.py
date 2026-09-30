"""Runs the ETL pipeline: Extract -> Transform -> Load.

    python pipeline.py              run once
    python pipeline.py --schedule   run every day at ETL_SCHEDULE_TIME (default 02:00)
"""

import logging
import sys
import time

import psycopg2
import schedule

import config
from extract import extract
from load import load
from transform import transform

logger = logging.getLogger("etl")


def with_retry(func):
    """Call func(). If the database can't be reached, wait and try again.

    The wait doubles after each failed try (2s, 4s, 8s...). After the last
    try the error is raised so the pipeline knows the stage failed.
    """
    delay = config.RETRY_DELAY_SECONDS
    for attempt in range(1, config.RETRY_ATTEMPTS + 1):
        try:
            return func()
        except psycopg2.OperationalError as error:
            if attempt == config.RETRY_ATTEMPTS:
                raise
            logger.warning("Attempt %d failed (%s). Retrying in %.0fs...", attempt, error, delay)
            time.sleep(delay)
            delay = delay * 2


def run_extract():
    conn = psycopg2.connect(**config.DB_SETTINGS)
    try:
        return extract(conn)
    finally:
        conn.close()


def run_load(tables):
    conn = psycopg2.connect(**config.DB_SETTINGS)
    try:
        load(conn, tables)
    finally:
        conn.close()


def run_pipeline():
    """Run all three stages. Returns True if it worked, False if a stage failed."""
    logger.info("ETL run started")

    try:
        accounts, instruments, orders = with_retry(run_extract)
    except Exception:
        logger.exception("EXTRACT stage failed")
        return False

    try:
        tables = transform(accounts, instruments, orders)
    except Exception:
        logger.exception("TRANSFORM stage failed")
        return False

    try:
        with_retry(lambda: run_load(tables))
    except Exception:
        logger.exception("LOAD stage failed")
        return False

    logger.info("ETL run finished: %d trades loaded", len(tables["fact_trades"]))
    return True


def run_on_schedule():
    schedule.every().day.at(config.SCHEDULE_TIME).do(run_pipeline)
    logger.info("Scheduler started: the pipeline will run every day at %s", config.SCHEDULE_TIME)
    while True:
        schedule.run_pending()
        time.sleep(30)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if "--schedule" in sys.argv:
        run_on_schedule()
    else:
        succeeded = run_pipeline()
        sys.exit(0 if succeeded else 1)
