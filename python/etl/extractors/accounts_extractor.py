"""Accounts Data Extractor"""

import pandas as pd

from etl.extractors.base import BaseExtractor
from etl.logging_config import get_logger

logger = get_logger(__name__)


class AccountsExtractor(BaseExtractor):
    """Extract accounts data from PostgreSQL"""
    
    TABLE_NAME = "accounts"
    
    def extract(self) -> pd.DataFrame:
        """Extract all accounts from PostgreSQL
        
        Returns:
            DataFrame with account records (id, account_id, holder_name, cash_balance, status)
        """
        try:
            with self.db_client as client:
                query = f"""
                    SELECT 
                        id,
                        account_id,
                        holder_name,
                        cash_balance,
                        status,
                        version,
                        last_updated
                    FROM {self.TABLE_NAME}
                    ORDER BY id
                """
                results = client.execute_query(query)
                
                # Convert results to DataFrame
                self.data = pd.DataFrame(results)
                
                logger.info(f"Extracted {self.row_count()} accounts from PostgreSQL")
                return self.data
                
        except Exception as e:
            logger.error(f"Failed to extract accounts: {str(e)}")
            raise
    
    def get_active_accounts(self) -> pd.DataFrame:
        """Get only active accounts
        
        Returns:
            DataFrame with active account records
        """
        if self.data is None:
            self.extract()
        
        active = self.data[self.data['status'] == 'ACTIVE'].copy()
        logger.info(f"Filtered to {len(active)} active accounts")
        return active
    
    def get_account_ids(self) -> list:
        """Get list of all account IDs
        
        Returns:
            List of account_id values
        """
        if self.data is None:
            self.extract()
        
        return self.data['account_id'].unique().tolist()
