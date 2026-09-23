"""Configuration Management for ETL Pipeline"""

import os
from dotenv import load_dotenv
from pathlib import Path

# Load .env from the parent directory (python folder)
env_path = Path(__file__).parent.parent / ".env"
load_dotenv(env_path)


class PostgreSQLConfig:
    """PostgreSQL Connection Configuration"""
    
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = int(os.getenv("POSTGRES_PORT", "5432"))
    database = os.getenv("POSTGRES_DB", "tns_capital")
    user = os.getenv("POSTGRES_USER", "tns-capital-db-user")
    password = os.getenv("POSTGRES_PASSWORD", "")
    
    @classmethod
    def get_connection_string(cls):
        """Return PostgreSQL connection string"""
        return (
            f"postgresql://{cls.user}:{cls.password}@"
            f"{cls.host}:{cls.port}/{cls.database}"
        )
    
    @classmethod
    def get_psycopg2_params(cls):
        """Return parameters for psycopg2 connection"""
        return {
            "host": cls.host,
            "port": cls.port,
            "database": cls.database,
            "user": cls.user,
            "password": cls.password,
        }


class SnowflakeConfig:
    """Snowflake Connection Configuration"""
    
    account = os.getenv("SNOWFLAKE_ACCOUNT", "")
    user = os.getenv("SNOWFLAKE_USER", "")
    password = os.getenv("SNOWFLAKE_PASSWORD", "")
    database = os.getenv("SNOWFLAKE_DATABASE", "TNS_ANALYTICS")
    schema = os.getenv("SNOWFLAKE_SCHEMA", "TRADING")
    warehouse = os.getenv("SNOWFLAKE_WAREHOUSE", "ANALYTICS_WH")
    
    @classmethod
    def get_connection_params(cls):
        """Return parameters for snowflake-connector-python"""
        return {
            "account": cls.account,
            "user": cls.user,
            "password": cls.password,
            "database": cls.database,
            "schema": cls.schema,
            "warehouse": cls.warehouse,
        }


class ETLConfig:
    """ETL Pipeline Configuration"""
    
    batch_size = int(os.getenv("BATCH_SIZE", "1000"))
    synthetic_orders_count = int(os.getenv("SYNTHETIC_ORDERS_COUNT", "50000"))
    historical_period_years = int(os.getenv("HISTORICAL_PERIOD_YEARS", "1"))
    log_level = os.getenv("LOG_LEVEL", "INFO")
    
    # Derived settings
    logs_dir = Path(__file__).parent.parent / "logs"
    
    @classmethod
    def ensure_logs_dir(cls):
        """Create logs directory if it doesn't exist"""
        cls.logs_dir.mkdir(exist_ok=True)


class DashboardConfig:
    """Dashboard Configuration"""
    
    port = int(os.getenv("DASH_PORT", "8050"))
    debug = os.getenv("DASH_DEBUG", "False").lower() in ("true", "1", "yes")
    refresh_interval_minutes = int(os.getenv("DASH_REFRESH_INTERVAL_MINUTES", "60"))


# Initialize logs directory
ETLConfig.ensure_logs_dir()
