"""Base Extractor Class"""

from abc import ABC, abstractmethod
from typing import List, Dict, Any
import pandas as pd

from etl.utils.postgres_client import PostgreSQLClient
from etl.logging_config import get_logger

logger = get_logger(__name__)


class BaseExtractor(ABC):
    """Abstract base class for data extractors"""
    
    def __init__(self):
        """Initialize base extractor"""
        self.db_client = PostgreSQLClient()
        self.data = None
    
    @abstractmethod
    def extract(self) -> pd.DataFrame:
        """Extract data from source
        
        Must be implemented by subclasses
        
        Returns:
            DataFrame with extracted data
        """
        pass
    
    def get_data(self) -> pd.DataFrame:
        """Get extracted data
        
        Returns:
            DataFrame with extracted data
        """
        if self.data is None:
            self.extract()
        return self.data
    
    def row_count(self) -> int:
        """Get row count of extracted data
        
        Returns:
            Number of rows
        """
        return len(self.data) if self.data is not None else 0
