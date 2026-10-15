"""Settings for the ETL pipeline.

Values come from environment variables. When running locally they are read
from the repo's .env file (the same one docker-compose uses).
"""

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

# How to connect to PostgreSQL
DB_SETTINGS = {
    "host": os.getenv("ETL_DB_HOST", "localhost"),
    "port": int(os.getenv("ETL_DB_PORT", "5432")),
    "dbname": os.getenv("ETL_DB_NAME", "tns_capital"),
    "user": os.getenv("DB_USER", "tns-capital-db-user"),
    "password": os.getenv("DB_PASSWORD", ""),
}

# What time of day the scheduled run happens (24-hour HH:MM)
SCHEDULE_TIME = os.getenv("ETL_SCHEDULE_TIME", "02:00")

# If the database can't be reached: how many tries, and how long to wait
# before the first retry (the wait doubles each time: 2s, 4s, 8s...)
RETRY_ATTEMPTS = int(os.getenv("ETL_RETRY_ATTEMPTS", "3"))
RETRY_DELAY_SECONDS = float(os.getenv("ETL_RETRY_DELAY_SECONDS", "2"))

# Alpaca market data API, for the daily price history. Leave the keys blank
# to skip loading prices.
ALPACA_API_KEY_ID = os.getenv("ALPACA_API_KEY_ID", "")
ALPACA_API_SECRET_KEY = os.getenv("ALPACA_API_SECRET_KEY", "")
ALPACA_DATA_URL = os.getenv("ALPACA_DATA_URL", "https://data.alpaca.markets")

# "sip" covers all US exchanges. The free plan allows it for data older than
# 15 minutes, which daily history always is. "iex" is the free live feed only.
PRICE_HISTORY_FEED = os.getenv("PRICE_HISTORY_FEED", "sip")

# How many years of daily prices the first run loads for each instrument
PRICE_BACKFILL_YEARS = int(os.getenv("PRICE_BACKFILL_YEARS", "5"))
