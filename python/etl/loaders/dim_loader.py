"""Dimension Table Loader"""

import pandas as pd
from typing import List

from etl.loaders.base import BaseLoader
from etl.logging_config import get_logger

logger = get_logger(__name__)


class DimLoader(BaseLoader):
    """Load dimension tables to Snowflake with upsert logic"""
    
    def load(self, data: pd.DataFrame, table_name: str, key_columns: List[str]) -> int:
        """Upsert dimension data to Snowflake
        
        Args:
            data: DataFrame with dimension data
            table_name: Target dimension table name
            key_columns: List of column names to use as merge key
        
        Returns:
            Number of rows upserted
        """
        try:
            with self.db_client as client:
                # Upsert using Snowflake merge
                client.upsert_dataframe(data, table_name, key_columns)
                
                # Get final row count
                final_count = client.get_row_count(table_name)
                logger.info(f"Dimension {table_name} now has {final_count} rows")
                
                return len(data)
                
        except Exception as e:
            logger.error(f"Failed to load {table_name}: {str(e)}")
            raise
    
    def load_dim_account(self, data: pd.DataFrame) -> int:
        """Load DIM_ACCOUNT with upsert
        
        Args:
            data: DataFrame with DIM_ACCOUNT data
        
        Returns:
            Number of rows upserted
        """
        return self.load(data, "DIM_ACCOUNT", ["ACCOUNT_ID"])
    
    def load_dim_instrument(self, data: pd.DataFrame) -> int:
        """Load DIM_INSTRUMENT with upsert
        
        Args:
            data: DataFrame with DIM_INSTRUMENT data
        
        Returns:
            Number of rows upserted
        """
        return self.load(data, "DIM_INSTRUMENT", ["SYMBOL"])
    
    def load_dim_date(self, data: pd.DataFrame) -> int:
        """Load DIM_DATE with upsert
        
        Args:
            data: DataFrame with DIM_DATE data
        
        Returns:
            Number of rows upserted
        """
        return self.load(data, "DIM_DATE", ["DATE_KEY"])
