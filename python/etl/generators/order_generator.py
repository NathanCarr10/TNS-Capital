"""Synthetic Order Data Generator"""

import pandas as pd
import random
import uuid
from datetime import datetime, timedelta
from typing import List

from etl.config import ETLConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class OrderGenerator:
    """Generate synthetic trading orders for testing and analytics"""
    
    # Valid order sides
    SIDES = ['BUY', 'SELL']
    
    # Valid order statuses
    STATUSES = ['NEW', 'FILLED', 'REJECTED', 'CANCELLED']
    
    def __init__(self, count: int = None):
        """Initialize order generator
        
        Args:
            count: Number of synthetic orders to generate (default: SYNTHETIC_ORDERS_COUNT from config)
        """
        self.count = count or ETLConfig.synthetic_orders_count
        self.account_ids = []
        self.symbols = []
        self.generated_orders = None
    
    def set_account_ids(self, account_ids: List[str]):
        """Set list of valid account IDs for generating orders
        
        Args:
            account_ids: List of account_id values from extracted accounts
        """
        if not account_ids:
            raise ValueError("Account IDs list cannot be empty")
        self.account_ids = account_ids
        logger.info(f"Set {len(account_ids)} account IDs for synthetic order generation")
    
    def set_symbols(self, symbols: List[str]):
        """Set list of valid instrument symbols for generating orders
        
        Args:
            symbols: List of symbol values from extracted instruments
        """
        if not symbols:
            raise ValueError("Symbols list cannot be empty")
        self.symbols = symbols
        logger.info(f"Set {len(symbols)} symbols for synthetic order generation")
    
    def generate(self) -> pd.DataFrame:
        """Generate synthetic orders
        
        Returns:
            DataFrame with synthetic order records
        """
        if not self.account_ids:
            raise ValueError("Account IDs must be set before generating orders")
        if not self.symbols:
            raise ValueError("Symbols must be set before generating orders")
        
        try:
            orders = []
            
            # Calculate date range for synthetic orders (1 year back)
            end_date = datetime.now()
            start_date = end_date - timedelta(days=ETLConfig.historical_period_years * 365)
            
            for _ in range(self.count):
                # Random but realistic values
                account_id = random.choice(self.account_ids)
                symbol = random.choice(self.symbols)
                side = random.choice(self.SIDES)
                quantity = random.randint(1, 1000)  # 1 to 1000 shares
                price = round(random.uniform(10.0, 500.0), 2)  # $10 to $500
                status = random.choice(self.STATUSES)
                
                # Random timestamp within the range
                time_delta = end_date - start_date
                random_seconds = random.randint(0, int(time_delta.total_seconds()))
                created_on = start_date + timedelta(seconds=random_seconds)
                
                # Generate unique idempotency key
                idempotency_key = f"SYNTH-{uuid.uuid4().hex[:20].upper()}"
                
                orders.append({
                    'account_id': account_id,
                    'symbol': symbol,
                    'side': side,
                    'quantity': quantity,
                    'price': price,
                    'status': status,
                    'created_on': created_on,
                    'idempotency_key': idempotency_key,
                    'version': 1,
                    'last_updated': datetime.now()
                })
            
            # Convert to DataFrame
            self.generated_orders = pd.DataFrame(orders)
            
            logger.info(f"Generated {self.count} synthetic orders")
            logger.info(f"  - BUY orders: {(self.generated_orders['side'] == 'BUY').sum()}")
            logger.info(f"  - SELL orders: {(self.generated_orders['side'] == 'SELL').sum()}")
            logger.info(f"  - FILLED orders: {(self.generated_orders['status'] == 'FILLED').sum()}")
            
            return self.generated_orders
            
        except Exception as e:
            logger.error(f"Failed to generate synthetic orders: {str(e)}")
            raise
    
    def get_generated_orders(self) -> pd.DataFrame:
        """Get generated orders
        
        Returns:
            DataFrame with synthetic orders
        """
        if self.generated_orders is None:
            raise ValueError("Orders have not been generated yet")
        return self.generated_orders
    
    def validate_orders(self, accounts_df: pd.DataFrame = None, instruments_df: pd.DataFrame = None) -> bool:
        """Validate generated orders against reference data
        
        Args:
            accounts_df: Optional DataFrame with valid accounts
            instruments_df: Optional DataFrame with valid instruments
        
        Returns:
            True if all orders are valid
        """
        if self.generated_orders is None:
            raise ValueError("Orders have not been generated yet")
        
        try:
            # Check quantities are positive
            if (self.generated_orders['quantity'] <= 0).any():
                logger.error("Found orders with non-positive quantities")
                return False
            
            # Check prices are positive
            if (self.generated_orders['price'] <= 0).any():
                logger.error("Found orders with non-positive prices")
                return False
            
            # Check sides are valid
            if not self.generated_orders['side'].isin(self.SIDES).all():
                logger.error("Found orders with invalid sides")
                return False
            
            # Check statuses are valid
            if not self.generated_orders['status'].isin(self.STATUSES).all():
                logger.error("Found orders with invalid statuses")
                return False
            
            # Validate against accounts if provided
            if accounts_df is not None:
                valid_accounts = set(accounts_df['account_id'].unique())
                invalid_accounts = self.generated_orders[
                    ~self.generated_orders['account_id'].isin(valid_accounts)
                ]['account_id'].unique()
                if len(invalid_accounts) > 0:
                    logger.error(f"Found orders with invalid account_ids: {invalid_accounts}")
                    return False
            
            # Validate against instruments if provided
            if instruments_df is not None:
                valid_symbols = set(instruments_df['symbol'].unique())
                invalid_symbols = self.generated_orders[
                    ~self.generated_orders['symbol'].isin(valid_symbols)
                ]['symbol'].unique()
                if len(invalid_symbols) > 0:
                    logger.error(f"Found orders with invalid symbols: {invalid_symbols}")
                    return False
            
            logger.info("Synthetic orders validation passed")
            return True
            
        except Exception as e:
            logger.error(f"Validation failed: {str(e)}")
            raise
