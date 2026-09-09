"""
Tests for ETL module.
"""

import pytest
import pandas as pd
from unittest.mock import patch, MagicMock
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from etl.extract import extract_accounts, extract_instruments, extract_orders, extract_positions


class TestExtraction:
    """Test data extraction functions."""
    
    @pytest.fixture
    def mock_dataframe(self):
        """Create a mock dataframe for testing."""
        return pd.DataFrame({
            'id': [1, 2, 3],
            'name': ['Item1', 'Item2', 'Item3']
        })
    
    @patch('etl.extract.pd.read_sql_query')
    @patch('etl.extract.get_db_connection')
    def test_extract_accounts(self, mock_conn, mock_read_sql, mock_dataframe):
        """Test accounts extraction."""
        mock_conn.return_value = MagicMock()
        mock_read_sql.return_value = mock_dataframe
        
        result = extract_accounts()
        
        assert isinstance(result, pd.DataFrame)
        assert len(result) == 3
    
    @patch('etl.extract.pd.read_sql_query')
    @patch('etl.extract.get_db_connection')
    def test_extract_connection_failure(self, mock_conn, mock_read_sql):
        """Test extraction with connection failure."""
        mock_conn.return_value = None
        
        result = extract_accounts()
        
        assert isinstance(result, pd.DataFrame)
        assert len(result) == 0


class TestDataFrameOperations:
    """Test dataframe operations and transformations."""
    
    def test_dataframe_creation(self):
        """Test basic dataframe creation."""
        df = pd.DataFrame({
            'symbol': ['ACME', 'GLOB'],
            'quantity': [100, 200],
            'price': [25.50, 10.00]
        })
        
        assert len(df) == 2
        assert 'symbol' in df.columns
        df['value'] = df['quantity'] * df['price']
        assert 'value' in df.columns


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
