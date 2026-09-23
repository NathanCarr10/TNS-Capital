"""Dimension Instrument Transformer"""

import pandas as pd

from etl.transformers.base import BaseTransformer
from etl.logging_config import get_logger

logger = get_logger(__name__)


class DimInstrumentTransformer(BaseTransformer):
    """Transform instruments data to DIM_INSTRUMENT dimensional format"""
    
    def transform(self, input_data: pd.DataFrame) -> pd.DataFrame:
        """Transform instruments to DIM_INSTRUMENT format
        
        Args:
            input_data: DataFrame with instruments data from PostgreSQL
        
        Returns:
            DataFrame in DIM_INSTRUMENT format
        """
        try:
            # Create a copy to avoid modifying original
            df = input_data.copy()
            
            # Select and rename required columns
            self.output_data = pd.DataFrame({
                'SYMBOL': df['symbol'],
                'NAME': df['name'],
                'ASSET_CLASS': df['asset_class'],
                'CURRENCY': df['currency']
            })
            
            # Remove duplicates (keep first occurrence)
            self.output_data = self.output_data.drop_duplicates(subset=['SYMBOL'], keep='first')
            
            logger.info(f"Transformed {self.row_count()} records to DIM_INSTRUMENT format")
            return self.output_data
            
        except Exception as e:
            logger.error(f"Failed to transform instruments: {str(e)}")
            raise
