"""Orders Data Extractor"""

import pandas as pd
from datetime import datetime, timedelta

from etl.extractors.base import BaseExtractor
from etl.config import ETLConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class OrdersExtractor(BaseExtractor):
    """Extract orders data from PostgreSQL"""
    
    TABLE_NAME = "orders"
    
    def extract(self, days_back: int = None) -> pd.DataFrame:
        """Extract orders from PostgreSQL
        
        Args:
            days_back: Number of days to look back (default: HISTORICAL_PERIOD_YEARS from config)
        
        Returns:
            DataFrame with order records
        """
        try:
            # Calculate lookback period
            if days_back is None:
                days_back = ETLConfig.historical_period_years * 365
            
            cutoff_date = datetime.now() - timedelta(days=days_back)
            
            with self.db_client as client:
                query = f"""
                    SELECT 
                        id,
                        account_id,
                        symbol,
                        side,
                        quantity,
                        price,
                        status,
                        idempotency_key,
                        created_on
                        -- version,
                        -- last_updated
                    FROM {self.TABLE_NAME}
                    WHERE created_on >= %s
                    ORDER BY created_on DESC
                """
                results = client.execute_query(query, (cutoff_date,))
                
                # Convert results to DataFrame
                self.data = pd.DataFrame(results)
                
                logger.info(f"Extracted {self.row_count()} orders from PostgreSQL (last {days_back} days)")
                return self.data
                
        except Exception as e:
            logger.error(f"Failed to extract orders: {str(e)}")
            raise
    
    def get_filled_orders(self) -> pd.DataFrame:
        """Get only filled orders
        
        Returns:
            DataFrame with filled order records
        """
        if self.data is None:
            self.extract()
        
        filled = self.data[self.data['status'] == 'FILLED'].copy()
        logger.info(f"Filtered to {len(filled)} filled orders")
        return filled
    
    def get_orders_by_status(self, status: str) -> pd.DataFrame:
        """Get orders filtered by status
        
        Args:
            status: Order status (NEW, FILLED, REJECTED, CANCELLED)
        
        Returns:
            DataFrame with orders of specified status
        """
        if self.data is None:
            self.extract()
        
        filtered = self.data[self.data['status'] == status].copy()
        logger.info(f"Filtered to {len(filtered)} orders with status {status}")
        return filtered
