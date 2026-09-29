"""Command line entry point.

    python -m tns_etl run [--since YYYY-MM-DD] [--until YYYY-MM-DD]
    python -m tns_etl schedule [--at HH:MM] [--run-now]
"""

import argparse
import logging
import sys
from datetime import datetime
from pathlib import Path

from tns_etl.config import EtlConfig, parse_schedule_time
from tns_etl.pipeline import EtlPipeline
from tns_etl.scheduler import DailyScheduler

logger = logging.getLogger("tns_etl")


def _load_dotenv() -> None:
    """Pick up DB_USER / DB_PASSWORD from the repo-root .env when running locally."""
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    load_dotenv(Path(__file__).resolve().parents[2] / ".env", override=False)


def _date(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%d")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="tns_etl", description="TNS Capital trade ETL pipeline")
    sub = parser.add_subparsers(dest="command", required=True)

    run_cmd = sub.add_parser("run", help="run the pipeline once")
    run_cmd.add_argument("--since", type=_date, help="only orders created on/after this date")
    run_cmd.add_argument("--until", type=_date, help="only orders created before this date")

    sched_cmd = sub.add_parser("schedule", help="run the pipeline every day at a fixed time")
    sched_cmd.add_argument("--at", type=parse_schedule_time,
                           help="time of day HH:MM (default: ETL_SCHEDULE_TIME or 02:00)")
    sched_cmd.add_argument("--run-now", action="store_true",
                           help="also run once immediately on startup")

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)-7s %(name)s - %(message)s")
    _load_dotenv()
    config = EtlConfig.from_env()
    pipeline = EtlPipeline(config)

    if args.command == "run":
        return 0 if pipeline.run(since=args.since, until=args.until).succeeded else 1

    scheduler = DailyScheduler(pipeline.run, run_at=args.at or config.schedule_time)
    scheduler.install_signal_handlers()
    scheduler.run_forever(run_immediately=args.run_now)
    return 0


if __name__ == "__main__":
    sys.exit(main())
