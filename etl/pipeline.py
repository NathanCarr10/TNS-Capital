"""Runs the ETL pipeline: Extract -> Transform -> Load.

Each run does two jobs:
    trades  orders, accounts and instruments from the app's tables -> star schema
    prices  daily price bars for every instrument from Alpaca -> fact_daily_prices

    python pipeline.py              run once
    python pipeline.py --schedule   run every day at ETL_SCHEDULE_TIME (default 02:00)
"""

import logging
import sys
import time
from datetime import date

import psycopg2
import schedule

import config
import market_data
from extract import extract, extract_last_price_dates
from load import load, load_prices
from transform import build_fact_daily_prices, transform

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


def run_price_extract():
    conn = psycopg2.connect(**config.DB_SETTINGS)
    try:
        return extract_last_price_dates(conn)
    finally:
        conn.close()


def run_price_load(prices):
    conn = psycopg2.connect(**config.DB_SETTINGS)
    try:
        load_prices(conn, prices)
    finally:
        conn.close()


def run_pipeline():
    """Run both jobs. Returns True if both worked, False if a stage failed.

    A failure in one job does not stop the other.
    """
    trades_ok = run_trades()
    prices_ok = run_prices()
    return trades_ok and prices_ok


def run_trades():
    """Run all three trade stages. Returns True if it worked, False if a stage failed."""
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


def run_prices():
    """Download new daily prices for every instrument and load them.

    Returns True if it worked or was skipped (no Alpaca keys), False if a stage failed.
    """
    if not market_data.is_configured():
        logger.warning("ALPACA_API_KEY_ID / ALPACA_API_SECRET_KEY are not set: skipping price history")
        return True

    logger.info("Price history run started")

    try:
        last_loaded = with_retry(run_price_extract)
        bars = {}
        for start, symbols in market_data.start_dates(last_loaded, date.today(), config.PRICE_BACKFILL_YEARS).items():
            bars.update(market_data.fetch_daily_bars(symbols, start))
    except Exception:
        logger.exception("PRICES EXTRACT stage failed")
        return False

    try:
        prices = build_fact_daily_prices(bars)
    except Exception:
        logger.exception("PRICES TRANSFORM stage failed")
        return False

    try:
        with_retry(lambda: run_price_load(prices))
    except Exception:
        logger.exception("PRICES LOAD stage failed")
        return False

    logger.info("Price history run finished: %d daily prices loaded", len(prices))
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
