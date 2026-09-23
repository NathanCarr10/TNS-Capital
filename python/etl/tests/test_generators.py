"""Unit tests for synthetic order generator"""

import unittest
import pandas as pd
from datetime import datetime, timedelta

from etl.generators.order_generator import OrderGenerator
from etl.config import ETLConfig


class TestOrderGenerator(unittest.TestCase):
    """Test cases for OrderGenerator"""
    
    def setUp(self):
        """Set up test fixtures"""
        self.generator = OrderGenerator(count=100)
        self.test_accounts = ['ACC-001', 'ACC-002', 'ACC-003']
        self.test_symbols = ['AAPL', 'GOOGL', 'MSFT']
    
    def test_initialization(self):
        """Test generator initialization"""
        self.assertEqual(self.generator.count, 100)
        self.assertIsNone(self.generator.generated_orders)
    
    def test_set_account_ids(self):
        """Test setting account IDs"""
        self.generator.set_account_ids(self.test_accounts)
        self.assertEqual(len(self.generator.account_ids), 3)
        self.assertIn('ACC-001', self.generator.account_ids)
    
    def test_set_symbols(self):
        """Test setting symbols"""
        self.generator.set_symbols(self.test_symbols)
        self.assertEqual(len(self.generator.symbols), 3)
        self.assertIn('AAPL', self.generator.symbols)
    
    def test_generate_without_accounts(self):
        """Test that generation fails without account IDs"""
        self.generator.set_symbols(self.test_symbols)
        with self.assertRaises(ValueError):
            self.generator.generate()
    
    def test_generate_without_symbols(self):
        """Test that generation fails without symbols"""
        self.generator.set_account_ids(self.test_accounts)
        with self.assertRaises(ValueError):
            self.generator.generate()
    
    def test_generate_orders(self):
        """Test successful order generation"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        self.assertEqual(len(orders), 100)
        self.assertIn('account_id', orders.columns)
        self.assertIn('symbol', orders.columns)
        self.assertIn('side', orders.columns)
        self.assertIn('quantity', orders.columns)
        self.assertIn('price', orders.columns)
        self.assertIn('status', orders.columns)
    
    def test_generated_orders_valid_sides(self):
        """Test that generated orders have valid sides"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        valid_sides = {'BUY', 'SELL'}
        self.assertTrue(orders['side'].isin(valid_sides).all())
    
    def test_generated_orders_valid_statuses(self):
        """Test that generated orders have valid statuses"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        valid_statuses = {'NEW', 'FILLED', 'REJECTED', 'CANCELLED'}
        self.assertTrue(orders['status'].isin(valid_statuses).all())
    
    def test_generated_orders_positive_quantities(self):
        """Test that generated orders have positive quantities"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        self.assertTrue((orders['quantity'] > 0).all())
    
    def test_generated_orders_positive_prices(self):
        """Test that generated orders have positive prices"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        self.assertTrue((orders['price'] > 0).all())
    
    def test_generated_orders_unique_keys(self):
        """Test that generated orders have unique idempotency keys"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        # All keys should be unique
        self.assertEqual(len(orders), len(orders['idempotency_key'].unique()))
    
    def test_validate_orders_valid(self):
        """Test validation of valid orders"""
        self.generator.set_account_ids(self.test_accounts)
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        # Create reference dataframes
        accounts_df = pd.DataFrame({'account_id': self.test_accounts})
        instruments_df = pd.DataFrame({'symbol': self.test_symbols})
        
        # Should pass validation
        is_valid = self.generator.validate_orders(accounts_df, instruments_df)
        self.assertTrue(is_valid)
    
    def test_validate_orders_invalid_account(self):
        """Test validation catches invalid account IDs"""
        self.generator.set_account_ids(['ACC-001'])  # Only one account
        self.generator.set_symbols(self.test_symbols)
        orders = self.generator.generate()
        
        # Reference with different accounts
        accounts_df = pd.DataFrame({'account_id': ['ACC-999']})
        instruments_df = pd.DataFrame({'symbol': self.test_symbols})
        
        # Should fail validation due to account mismatch
        is_valid = self.generator.validate_orders(accounts_df, instruments_df)
        self.assertFalse(is_valid)


if __name__ == '__main__':
    unittest.main()
