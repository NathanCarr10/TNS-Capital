"""Runtime configuration for the ETL pipeline, read from environment variables."""

import os
import re
from dataclasses import dataclass, field
from datetime import time
from typing import Mapping, Optional

_IDENTIFIER = re.compile(r"^[a-z_][a-z0-9_]*$")


@dataclass(frozen=True)
class RetryPolicy:
    """How often, and how patiently, a failed stage is retried."""

    max_attempts: int = 3
    initial_delay_seconds: float = 2.0
    backoff_multiplier: float = 2.0
    max_delay_seconds: float = 60.0

    def __post_init__(self):
        if self.max_attempts < 1:
            raise ValueError("max_attempts must be at least 1")
        if self.initial_delay_seconds < 0 or self.max_delay_seconds < 0:
            raise ValueError("retry delays cannot be negative")
        if self.backoff_multiplier < 1:
            raise ValueError("backoff_multiplier must be >= 1")


@dataclass(frozen=True)
class EtlConfig:
    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "tns_capital"
    db_user: str = "tns-capital-db-user"
    db_password: str = ""
    analytics_schema: str = "analytics"
    schedule_time: time = time(2, 0)
    retry: RetryPolicy = field(default_factory=RetryPolicy)

    def __post_init__(self):
        # The schema name is interpolated into SQL, so only allow plain identifiers.
        if not _IDENTIFIER.match(self.analytics_schema):
            raise ValueError(f"Invalid analytics schema name: {self.analytics_schema!r}")

    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "EtlConfig":
        """Build config from environment variables.

        DB_USER / DB_PASSWORD are shared with docker-compose.yml and the Spring app.
        """
        env = os.environ if env is None else env
        return cls(
            db_host=env.get("ETL_DB_HOST", "localhost"),
            db_port=int(env.get("ETL_DB_PORT", "5432")),
            db_name=env.get("ETL_DB_NAME", "tns_capital"),
            db_user=env.get("DB_USER", "tns-capital-db-user"),
            db_password=env.get("DB_PASSWORD", ""),
            analytics_schema=env.get("ETL_ANALYTICS_SCHEMA", "analytics"),
            schedule_time=parse_schedule_time(env.get("ETL_SCHEDULE_TIME", "02:00")),
            retry=RetryPolicy(
                max_attempts=int(env.get("ETL_RETRY_MAX_ATTEMPTS", "3")),
                initial_delay_seconds=float(env.get("ETL_RETRY_INITIAL_DELAY_SECONDS", "2")),
                backoff_multiplier=float(env.get("ETL_RETRY_BACKOFF_MULTIPLIER", "2")),
                max_delay_seconds=float(env.get("ETL_RETRY_MAX_DELAY_SECONDS", "60")),
            ),
        )

    def connection_kwargs(self) -> dict:
        return {
            "host": self.db_host,
            "port": self.db_port,
            "dbname": self.db_name,
            "user": self.db_user,
            "password": self.db_password,
            "connect_timeout": 10,
            "application_name": "tns-capital-etl",
        }


def parse_schedule_time(value: str) -> time:
    """Parse an HH:MM string such as '02:00' into a time."""
    try:
        hours, minutes = value.strip().split(":")
        return time(int(hours), int(minutes))
    except ValueError as exc:
        raise ValueError(f"ETL_SCHEDULE_TIME must be HH:MM, got {value!r}") from exc
