"""Fact Table Loader"""

import pandas as pd

from etl.loaders.base import BaseLoader
from etl.logging_config import get_logger

logger = get_logger(__name__)


class FactLoader(BaseLoader):
    """Load fact tables to Snowflake with merge logic for idempotency"""
    
    def load(self, data: pd.DataFrame, table_name: str) -> int:
        """Insert fact data to Snowflake with idempotency handling
        
        Uses MERGE logic to handle duplicate records on re-runs
        
        Args:
            data: DataFrame with fact data
            table_name: Target fact table name
        
        Returns:
            Number of rows inserted
        """
        try:
            with self.db_client as client:
                # Insert data using write_pandas (simpler than full merge)
                # For FACT_TRADES, we use IDEMPOTENCY_KEY to avoid duplicates
                rows_inserted = client.insert_dataframe(data, table_name, if_exists="append")
                
                # Get final row count
                final_count = client.get_row_count(table_name)
                logger.info(f"Fact table {table_name} now has {final_count} rows")
                
                return rows_inserted
                
        except Exception as e:
            logger.error(f"Failed to load {table_name}: {str(e)}")
            raise
    
    def load_fact_trades(self, data: pd.DataFrame) -> int:
        """Load FACT_TRADES
        
        Args:
            data: DataFrame with FACT_TRADES data
        
        Returns:
            Number of rows inserted
        """
        try:
            # Validate required columns
            required_cols = ['TRADE_KEY', 'ACCOUNT_KEY', 'INSTRUMENT_KEY', 'DATE_KEY', 'SIDE', 'QUANTITY', 'PRICE', 'STATUS', 'IDEMPOTENCY_KEY']
            missing_cols = [col for col in required_cols if col not in data.columns]
            if missing_cols:
                raise ValueError(f"Missing required columns: {missing_cols}")
            
            # For idempotency, we'll implement a MERGE in Snowflake
            with self.db_client as client:
                # Create temp table
                temp_table_name = "FACT_TRADES_TEMP"
                
                # Insert into temp table
                client.insert_dataframe(data, temp_table_name, if_exists="replace")
                
                # Execute MERGE to handle duplicates based on IDEMPOTENCY_KEY
                merge_sql = f"""
                    MERGE INTO FACT_TRADES t
                    USING {temp_table_name} s
                    ON t.IDEMPOTENCY_KEY = s.IDEMPOTENCY_KEY
                    WHEN NOT MATCHED THEN
                        INSERT (TRADE_KEY, ACCOUNT_KEY, INSTRUMENT_KEY, DATE_KEY, SIDE, QUANTITY, PRICE, STATUS, CREATED_ON, IDEMPOTENCY_KEY)
                        VALUES (s.TRADE_KEY, s.ACCOUNT_KEY, s.INSTRUMENT_KEY, s.DATE_KEY, s.SIDE, s.QUANTITY, s.PRICE, s.STATUS, s.CREATED_ON, s.IDEMPOTENCY_KEY)
                """
                
                client.execute_update(merge_sql)
                
                # Drop temp table
                client.execute_update(f"DROP TABLE {temp_table_name}")
                
                # Get row count
                final_count = client.get_row_count("FACT_TRADES")
                logger.info(f"FACT_TRADES now has {final_count} rows after merge")
                
                return len(data)
                
        except Exception as e:
            logger.error(f"Failed to load FACT_TRADES: {str(e)}")
            raise
