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
