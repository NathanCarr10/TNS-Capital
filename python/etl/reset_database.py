"""Snowflake Database Reset Module

Provides functionality to drop and recreate the Snowflake database
with all dimension and fact tables for the trading analytics system.
"""

import snowflake.connector
from snowflake.connector import ProgrammingError
import sys
from pathlib import Path
from typing import Optional

# Add python directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from etl.config import SnowflakeConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class SnowflakeResetManager:
    """Manages Snowflake database reset operations"""
    
    def __init__(self, dry_run: bool = False):
        """Initialize reset manager
        
        Args:
            dry_run: If True, preview SQL without executing
        """
        self.dry_run = dry_run
        self.config = SnowflakeConfig()
        self.connection = None
        self.cursor = None
        
    def connect(self) -> None:
        """Establish connection to Snowflake"""
        try:
            logger.info(f"Connecting to Snowflake: {self.config.account}")
            self.connection = snowflake.connector.connect(**self.config.get_connection_params())
            self.cursor = self.connection.cursor()
            logger.info("✓ Connected to Snowflake")
        except Exception as e:
            logger.error(f"Failed to connect to Snowflake: {str(e)}")
            raise
    
    def disconnect(self) -> None:
        """Close Snowflake connection"""
        if self.cursor:
            self.cursor.close()
        if self.connection:
            self.connection.close()
            logger.info("✓ Disconnected from Snowflake")
    
    def execute_sql(self, sql: str, description: str = "") -> None:
        """Execute SQL statement
        
        Args:
            sql: SQL statement to execute
            description: Description of the operation
        """
        if self.dry_run:
            print(f"\n[DRY-RUN SQL]{' - ' + description if description else ''}")
            print(sql)
            return
        
        try:
            if description:
                logger.info(description)
            self.cursor.execute(sql)
            logger.info(f"✓ {description}" if description else "✓ Query executed")
        except ProgrammingError as e:
            logger.error(f"SQL Error: {str(e)}")
            raise
    
    def reset_database(self) -> None:
        """Drop and recreate the database"""
        logger.info("")
        logger.info("=== Phase 1: Database Reset ===")
        logger.info("")
        
        db = self.config.database
        schema = self.config.schema
        
        # Drop existing database
        drop_sql = f"DROP DATABASE IF EXISTS {db} CASCADE;"
        self.execute_sql(drop_sql, f"Dropping database: {db}")
        
        # Create new database
        create_db_sql = f"CREATE DATABASE {db};"
        self.execute_sql(create_db_sql, f"Creating database: {db}")
        
        # Create schema
        create_schema_sql = f"CREATE SCHEMA {db}.{schema};"
        self.execute_sql(create_schema_sql, f"Creating schema: {db}.{schema}")
        
        logger.info("")
    
    def create_tables(self) -> None:
        """Create all dimension and fact tables"""
        logger.info("=== Phase 2: Create Tables ===")
        logger.info("")
        
        db = self.config.database
        schema = self.config.schema
        full_schema = f"{db}.{schema}"
        
        # DIM_ACCOUNT
        dim_account_sql = f"""
        CREATE TABLE IF NOT EXISTS {full_schema}.DIM_ACCOUNT (
            ACCOUNT_KEY         NUMBER PRIMARY KEY,
            ACCOUNT_ID          VARCHAR NOT NULL UNIQUE,
            HOLDER_NAME         VARCHAR NOT NULL,
            STATUS              VARCHAR NOT NULL,
            EFFECTIVE_DATE      DATE NOT NULL,
            LOAD_TIMESTAMP      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP()
        );
        """
        self.execute_sql(dim_account_sql, "Creating DIM_ACCOUNT table")
        
        # DIM_INSTRUMENT
        dim_instrument_sql = f"""
        CREATE TABLE IF NOT EXISTS {full_schema}.DIM_INSTRUMENT (
            INSTRUMENT_KEY      NUMBER PRIMARY KEY,
            SYMBOL              VARCHAR NOT NULL UNIQUE,
            NAME                VARCHAR NOT NULL,
            ASSET_CLASS         VARCHAR NOT NULL,
            CURRENCY            VARCHAR NOT NULL,
            TRADABLE            BOOLEAN DEFAULT TRUE,
            LOAD_TIMESTAMP      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP()
        );
        """
        self.execute_sql(dim_instrument_sql, "Creating DIM_INSTRUMENT table")
        
        # DIM_DATE
        dim_date_sql = f"""
        CREATE TABLE IF NOT EXISTS {full_schema}.DIM_DATE (
            DATE_KEY            NUMBER PRIMARY KEY,
            FULL_DATE           DATE NOT NULL UNIQUE,
            DAY                 NUMBER NOT NULL,
            MONTH               NUMBER NOT NULL,
            YEAR                NUMBER NOT NULL,
            QUARTER             NUMBER NOT NULL
        );
        """
        self.execute_sql(dim_date_sql, "Creating DIM_DATE table")
        
        # FACT_TRADES
        fact_trades_sql = f"""
        CREATE TABLE IF NOT EXISTS {full_schema}.FACT_TRADES (
            TRADE_KEY           NUMBER PRIMARY KEY,
            ACCOUNT_KEY         NUMBER NOT NULL,
            INSTRUMENT_KEY      NUMBER NOT NULL,
            DATE_KEY            NUMBER NOT NULL,
            SIDE                VARCHAR NOT NULL,
            QUANTITY            NUMBER NOT NULL,
            PRICE               DECIMAL(18, 8) NOT NULL,
            STATUS              VARCHAR NOT NULL,
            CREATED_ON          NUMBER NOT NULL,
            IDEMPOTENCY_KEY     VARCHAR NOT NULL UNIQUE,
            CONSTRAINT fk_account FOREIGN KEY (ACCOUNT_KEY) REFERENCES {full_schema}.DIM_ACCOUNT(ACCOUNT_KEY),
            CONSTRAINT fk_instrument FOREIGN KEY (INSTRUMENT_KEY) REFERENCES {full_schema}.DIM_INSTRUMENT(INSTRUMENT_KEY),
            CONSTRAINT fk_date FOREIGN KEY (DATE_KEY) REFERENCES {full_schema}.DIM_DATE(DATE_KEY)
        );
        """
        self.execute_sql(fact_trades_sql, "Creating FACT_TRADES table")
        
        logger.info("")
    
    def verify_tables(self) -> None:
        """Verify tables and display summary"""
        if self.dry_run:
            logger.info("Dry-run complete. No verification performed.")
            return
        
        logger.info("=== Phase 3: Verification ===")
        logger.info("")
        
        db = self.config.database
        schema = self.config.schema
        full_schema = f"{db}.{schema}"
        
        tables = ["DIM_ACCOUNT", "DIM_INSTRUMENT", "DIM_DATE", "FACT_TRADES"]
        
        for table in tables:
            try:
                count_sql = f"SELECT COUNT(*) AS row_count FROM {full_schema}.{table};"
                self.cursor.execute(count_sql)
                result = self.cursor.fetchone()
                row_count = result[0] if result else 0
                logger.info(f"✓ {table}: {row_count} rows")
            except Exception as e:
                logger.warning(f"✗ {table}: Failed to verify - {str(e)}")
        
        logger.info("")
    
    def reset(self) -> None:
        """Execute full database reset"""
        try:
            print("")
            print("╔════════════════════════════════════════════════════════════════════════╗")
            print("║                 Snowflake Database Reset                              ║")
            print("╚════════════════════════════════════════════════════════════════════════╝")
            print("")
            
            if self.dry_run:
                logger.warning("Running in DRY-RUN mode (no changes will be made)")
                print("")
            
            logger.info("=== Phase 0: Validation ===")
            logger.info(f"Account: {self.config.account}")
            logger.info(f"Database: {self.config.database}")
            logger.info(f"Schema: {self.config.schema}")
            logger.info(f"Warehouse: {self.config.warehouse}")
            
            self.connect()
            
            self.reset_database()
            self.create_tables()
            self.verify_tables()
            
            print("╔════════════════════════════════════════════════════════════════════════╗")
            if self.dry_run:
                logger.warning("Dry-run completed. Review the SQL above and re-run without --dry-run to execute.")
            else:
                logger.info("Database reset completed successfully!")
            print("╚════════════════════════════════════════════════════════════════════════╝")
            print("")
            
        except Exception as e:
            logger.error(f"Reset failed: {str(e)}")
            raise
        finally:
            self.disconnect()


def main() -> int:
    """Main entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Reset Snowflake database with dimension and fact tables"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview SQL commands without executing"
    )
    
    args = parser.parse_args()
    
    try:
        manager = SnowflakeResetManager(dry_run=args.dry_run)
        manager.reset()
        return 0
    except Exception as e:
        logger.error(f"Fatal error: {str(e)}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
