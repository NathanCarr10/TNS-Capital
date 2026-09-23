"""Dimension Account Transformer"""

import pandas as pd
from datetime import datetime

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
            
            # Select and rename required columns
            self.output_data = pd.DataFrame({
                'ACCOUNT_ID': df['account_id'],
                'HOLDER_NAME': df['holder_name'],
                'STATUS': df['status'],
                'EFFECTIVE_DATE': datetime.now()
            })
            
            # Remove duplicates (keep latest by account_id)
            self.output_data = self.output_data.drop_duplicates(subset=['ACCOUNT_ID'], keep='first')
            
            logger.info(f"Transformed {self.row_count()} records to DIM_ACCOUNT format")
            return self.output_data
            
        except Exception as e:
            logger.error(f"Failed to transform accounts: {str(e)}")
            raise
