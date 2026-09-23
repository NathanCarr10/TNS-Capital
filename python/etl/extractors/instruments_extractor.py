"""Instruments Data Extractor"""

import pandas as pd

from etl.extractors.base import BaseExtractor
from etl.logging_config import get_logger

logger = get_logger(__name__)


class InstrumentsExtractor(BaseExtractor):
    """Extract instruments data from PostgreSQL"""
    
    TABLE_NAME = "instruments"
    
    def extract(self) -> pd.DataFrame:
        """Extract all instruments from PostgreSQL
        
        Returns:
            DataFrame with instrument records (symbol, name, asset_class, currency, tradable)
        """
        try:
            with self.db_client as client:
                query = f"""
                    SELECT 
                        symbol,
                        name,
                        asset_class,
                        currency,
                        tradable
                    FROM {self.TABLE_NAME}
                    ORDER BY symbol
                """
                results = client.execute_query(query)
                
                # Convert results to DataFrame
                self.data = pd.DataFrame(results)
                
                logger.info(f"Extracted {self.row_count()} instruments from PostgreSQL")
                return self.data
                
        except Exception as e:
            logger.error(f"Failed to extract instruments: {str(e)}")
            raise
    
    def get_tradable_instruments(self) -> pd.DataFrame:
        """Get only tradable instruments
        
        Returns:
            DataFrame with tradable instrument records
        """
        if self.data is None:
            self.extract()
        
        tradable = self.data[self.data['tradable'] == True].copy()
        logger.info(f"Filtered to {len(tradable)} tradable instruments")
        return tradable
    
    def get_symbols(self) -> list:
        """Get list of all instrument symbols
        
        Returns:
            List of symbol values
        """
        if self.data is None:
            self.extract()
        
        return self.data['symbol'].unique().tolist()
