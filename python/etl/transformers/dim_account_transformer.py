"""Dimension Account Transformer"""

import pandas as pd
from datetime import datetime, date

from etl.transformers.base import BaseTransformer
from etl.logging_config import get_logger

logger = get_logger(__name__)


class DimAccountTransformer(BaseTransformer):
    """Transform accounts data to DIM_ACCOUNT dimensional format"""
    
    def transform(self, input_data: pd.DataFrame) -> pd.DataFrame:
        """Transform accounts to DIM_ACCOUNT format
        
        Args:
            input_data: DataFrame with accounts data from PostgreSQL
        
        Returns:
            DataFrame in DIM_ACCOUNT format
        """
        try:
            # Create a copy to avoid modifying original
            df = input_data.copy()
            
            # Remove duplicates (keep latest by account_id)
            df = df.drop_duplicates(subset=['account_id'], keep='first').reset_index(drop=True)
            
            # Generate surrogate keys (use hash of account_id for deterministic generation)
            df['ACCOUNT_KEY'] = (df['account_id'].astype(str).apply(hash) % 2147483647).abs() + 1000000
            
            # Select and rename required columns
            # Use Python date objects (not Timestamp) for proper Snowflake DATE type mapping
            self.output_data = pd.DataFrame({
                'ACCOUNT_KEY': df['ACCOUNT_KEY'],
                'ACCOUNT_ID': df['account_id'],
                'HOLDER_NAME': df['holder_name'],
                'STATUS': df['status'],
                'EFFECTIVE_DATE': date.today()
            })
            
            logger.info(f"Transformed {self.row_count()} records to DIM_ACCOUNT format")
            return self.output_data
            
        except Exception as e:
            logger.error(f"Failed to transform accounts: {str(e)}")
            raise
