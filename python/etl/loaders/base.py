"""Base Loader Class"""

from abc import ABC, abstractmethod
import pandas as pd

from etl.utils.snowflake_client import SnowflakeClient
from etl.logging_config import get_logger

logger = get_logger(__name__)


class BaseLoader(ABC):
    """Abstract base class for data loaders"""
    
    def __init__(self):
        """Initialize base loader"""
        self.db_client = SnowflakeClient()
    
    @abstractmethod
    def load(self, data: pd.DataFrame, table_name: str) -> int:
        """Load data to Snowflake
        
        Must be implemented by subclasses
        
        Args:
            data: DataFrame to load
            table_name: Target table name
        
        Returns:
            Number of rows loaded
        """
        pass
