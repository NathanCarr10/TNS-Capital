"""Fact Trades Transformer"""

import pandas as pd
from datetime import datetime

from etl.transformers.base import BaseTransformer
from etl.logging_config import get_logger

logger = get_logger(__name__)


class FactTradesTransformer(BaseTransformer):
    """Transform orders data to FACT_TRADES dimensional format with key lookups"""
    
    def __init__(self):
        """Initialize transformer with dimension lookup tables"""
        super().__init__()
        self.dim_account = None
        self.dim_instrument = None
        self.dim_date = None
    
    def set_dimensions(self, dim_account: pd.DataFrame, dim_instrument: pd.DataFrame, dim_date: pd.DataFrame):
        """Set dimension lookup tables
        
        Args:
            dim_account: DIM_ACCOUNT DataFrame (must have ACCOUNT_ID and ACCOUNT_KEY)
            dim_instrument: DIM_INSTRUMENT DataFrame (must have SYMBOL and INSTRUMENT_KEY)
            dim_date: DIM_DATE DataFrame (must have FULL_DATE and DATE_KEY)
        """
        self.dim_account = dim_account.copy()
        self.dim_instrument = dim_instrument.copy()
        self.dim_date = dim_date.copy()
        logger.info("Dimension tables loaded for fact transformer")
    
    def transform(self, input_data: pd.DataFrame) -> pd.DataFrame:
        """Transform orders to FACT_TRADES format with dimensional lookups
        
        Args:
            input_data: DataFrame with orders data from PostgreSQL
        
        Returns:
            DataFrame in FACT_TRADES format
        """
        if self.dim_account is None or self.dim_instrument is None or self.dim_date is None:
            raise ValueError("Dimension tables must be set before transforming")
        
        # Validate dimension tables have required columns
        required_account = {'ACCOUNT_ID', 'ACCOUNT_KEY'}
        required_instrument = {'SYMBOL', 'INSTRUMENT_KEY'}
        required_date = {'FULL_DATE', 'DATE_KEY'}
        
        if not required_account.issubset(self.dim_account.columns):
            missing = required_account - set(self.dim_account.columns)
            raise ValueError(f"DIM_ACCOUNT missing required columns: {missing}")
        if not required_instrument.issubset(self.dim_instrument.columns):
            missing = required_instrument - set(self.dim_instrument.columns)
            raise ValueError(f"DIM_INSTRUMENT missing required columns: {missing}")
        if not required_date.issubset(self.dim_date.columns):
            missing = required_date - set(self.dim_date.columns)
            raise ValueError(f"DIM_DATE missing required columns: {missing}")
        
        try:
            # Create a copy to avoid modifying original
            df = input_data.copy()
            
            # Prepare date dimension for joining - use Python date objects
            df['ORDER_DATE'] = pd.to_datetime(df['created_on']).dt.date
            
            # Prepare dimension tables for joining
            dim_account_lookup = self.dim_account[['ACCOUNT_ID', 'ACCOUNT_KEY']].drop_duplicates()
            dim_instrument_lookup = self.dim_instrument[['SYMBOL', 'INSTRUMENT_KEY']].drop_duplicates()
            dim_date_lookup = self.dim_date[['FULL_DATE', 'DATE_KEY']].copy()
            
            # Join with dimensions
            # Account lookup
            df = df.merge(
                dim_account_lookup,
                left_on='account_id',
                right_on='ACCOUNT_ID',
                how='left'
            )
            
            # Instrument lookup
            df = df.merge(
                dim_instrument_lookup,
                left_on='symbol',
                right_on='SYMBOL',
                how='left'
            )
            
            # Date lookup
            df = df.merge(
                dim_date_lookup,
                left_on='ORDER_DATE',
                right_on='FULL_DATE',
                how='left'
            )
            
            # Check for unmatched records (these would indicate data quality issues)
            unmatched_accounts = df['ACCOUNT_KEY'].isna().sum()
            unmatched_instruments = df['INSTRUMENT_KEY'].isna().sum()
            unmatched_dates = df['DATE_KEY'].isna().sum()
            
            if unmatched_accounts > 0:
                logger.warning(f"Found {unmatched_accounts} orders with unmatched accounts")
            if unmatched_instruments > 0:
                logger.warning(f"Found {unmatched_instruments} orders with unmatched instruments")
            if unmatched_dates > 0:
                logger.warning(f"Found {unmatched_dates} orders with unmatched dates")
            
            # Select and rename required columns for FACT_TRADES
            self.output_data = pd.DataFrame({
                'ACCOUNT_KEY': df['ACCOUNT_KEY'],
                'INSTRUMENT_KEY': df['INSTRUMENT_KEY'],
                'DATE_KEY': df['DATE_KEY'],
                'SIDE': df['side'],
                'QUANTITY': df['quantity'],
                'PRICE': df['price'],
                'STATUS': df['status'],
                'CREATED_ON': pd.to_datetime(df['created_on']),
                'IDEMPOTENCY_KEY': df['idempotency_key']
            })
            
            # Generate TRADE_KEY surrogate (hash-based, deterministic)
            self.output_data['TRADE_KEY'] = (
                self.output_data['IDEMPOTENCY_KEY'].astype(str).apply(hash) % 2147483647
            ).abs() + 3000000
            
            # Drop rows with any null foreign keys
            self.output_data = self.output_data.dropna(subset=['ACCOUNT_KEY', 'INSTRUMENT_KEY', 'DATE_KEY'])
            
            # Reset index
            self.output_data = self.output_data.reset_index(drop=True)
            
            logger.info(f"Transformed {self.row_count()} orders to FACT_TRADES format")
            return self.output_data
            
        except Exception as e:
            logger.error(f"Failed to transform orders to fact trades: {str(e)}")
            raise
