"""Dimension Date Transformer"""

import pandas as pd
from datetime import datetime, timedelta

from etl.transformers.base import BaseTransformer
from etl.config import ETLConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class DimDateTransformer(BaseTransformer):
    """Transform/generate DIM_DATE (calendar table) dimensional format"""
    
    def transform(self, input_data: pd.DataFrame = None, start_date: datetime = None, end_date: datetime = None) -> pd.DataFrame:
        """Generate DIM_DATE records for a date range
        
        If input_data is provided (with date columns), extracts unique dates from it.
        Otherwise, generates dates between start_date and end_date.
        
        Args:
            input_data: Optional DataFrame with date columns to extract dates from
            start_date: Start date (default: HISTORICAL_PERIOD_YEARS ago)
            end_date: End date (default: today)
        
        Returns:
            DataFrame in DIM_DATE format
        """
        try:
            # Determine date range
            if start_date is None:
                start_date = datetime.now() - timedelta(days=ETLConfig.historical_period_years * 365)
            if end_date is None:
                end_date = datetime.now()
            
            # Generate all dates in range
            date_range = pd.date_range(start=start_date, end=end_date, freq='D')
            
            # Create dataframe with date dimension - use Python date objects for Snowflake DATE type
            self.output_data = pd.DataFrame({
                'DATE_KEY': date_range.strftime('%Y%m%d').astype(int),
                'FULL_DATE': date_range.date,  # Python date objects for proper Snowflake DATE mapping
                'DAY': date_range.day,
                'MONTH': date_range.month,
                'YEAR': date_range.year,
                'QUARTER': date_range.quarter
            })
            
            # If input_data provided, filter to only dates present in the data
            if input_data is not None and 'created_on' in input_data.columns:
                created_dates = pd.to_datetime(input_data['created_on'], errors='coerce').dt.date.dropna().unique()
                self.output_data = self.output_data[
                    self.output_data['FULL_DATE'].isin(created_dates)
                ].reset_index(drop=True)
            
            logger.info(f"Generated {self.row_count()} date records")
            return self.output_data
            
        except Exception as e:
            logger.error(f"Failed to generate date dimension: {str(e)}")
            raise
