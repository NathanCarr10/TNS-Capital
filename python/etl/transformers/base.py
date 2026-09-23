"""Base Transformer Class"""

from abc import ABC, abstractmethod
import pandas as pd

from etl.logging_config import get_logger

logger = get_logger(__name__)


class BaseTransformer(ABC):
    """Abstract base class for data transformers"""
    
    def __init__(self):
        """Initialize base transformer"""
        self.input_data = None
        self.output_data = None
    
    @abstractmethod
    def transform(self, input_data: pd.DataFrame) -> pd.DataFrame:
        """Transform input data to output format
        
        Must be implemented by subclasses
        
        Args:
            input_data: DataFrame with source data
        
        Returns:
            DataFrame with transformed data
        """
        pass
    
    def get_output(self) -> pd.DataFrame:
        """Get transformed data
        
        Returns:
            DataFrame with transformed data
        """
        return self.output_data
    
    def row_count(self) -> int:
        """Get row count of output data
        
        Returns:
            Number of rows in output
        """
        return len(self.output_data) if self.output_data is not None else 0
