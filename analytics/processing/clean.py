"""
Clean module: Data cleaning, validation, and transformation.
"""

import pandas as pd
import numpy as np
import logging

logger = logging.getLogger(__name__)


def validate_data_quality(data):
    """
    Perform basic data quality checks on extracted data.
    
    Args:
        data (dict): Dictionary containing accounts, instruments, orders, positions
        
    Returns:
        dict: Validation results with any issues found
    """
    issues = []
    
    # Check for null values in critical columns
    if 'accounts' in data:
        accounts_null = data['accounts'].isnull().sum()
        if accounts_null.any():
            issues.append(f"Null values in accounts: {accounts_null[accounts_null > 0].to_dict()}")
    
    if 'orders' in data:
        orders_null = data['orders'].isnull().sum()
        if orders_null.any():
            issues.append(f"Null values in orders: {orders_null[orders_null > 0].to_dict()}")
    
    # Check referential integrity
    if 'orders' in data and 'accounts' in data:
        orphaned_orders = data['orders'][~data['orders']['account_id'].isin(data['accounts']['id'])]
        if len(orphaned_orders) > 0:
            issues.append(f"Found {len(orphaned_orders)} orphaned orders (invalid account_id)")
    
    if issues:
        for issue in issues:
            logger.warning(f"Data quality issue: {issue}")
    else:
        logger.info("Data quality checks passed")
    
    return {'issues': issues, 'passed': len(issues) == 0}


def clean_orders(orders_df):
    """
    Clean and enrich orders data.
    
    Args:
        orders_df (pd.DataFrame): Raw orders data
        
    Returns:
        pd.DataFrame: Cleaned orders data
    """
    df = orders_df.copy()
    
    # Ensure correct data types
    df['created_on'] = pd.to_datetime(df['created_on'])
    df['quantity'] = df['quantity'].astype(int)
    df['price'] = pd.to_numeric(df['price'], errors='coerce')
    
    # Add calculated fields
    df['order_value'] = df['quantity'] * df['price']
    df['order_date'] = df['created_on'].dt.date
    df['order_hour'] = df['created_on'].dt.hour
    
    logger.info(f"Cleaned {len(df)} orders")
    return df


def clean_positions(positions_df):
    """
    Clean and enrich positions data.
    
    Args:
        positions_df (pd.DataFrame): Raw positions data
        
    Returns:
        pd.DataFrame: Cleaned positions data
    """
    df = positions_df.copy()
    
    # Ensure correct data types
    df['quantity'] = df['quantity'].astype(int)
    df['average_cost'] = pd.to_numeric(df['average_cost'], errors='coerce')
    
    # Add calculated fields
    df['position_value'] = df['quantity'] * df['average_cost']
    
    logger.info(f"Cleaned {len(df)} positions")
    return df


def clean_accounts(accounts_df):
    """
    Clean and enrich accounts data.
    
    Args:
        accounts_df (pd.DataFrame): Raw accounts data
        
    Returns:
        pd.DataFrame: Cleaned accounts data
    """
    df = accounts_df.copy()
    
    # Ensure correct data types
    df['cash_balance'] = pd.to_numeric(df['cash_balance'], errors='coerce')
    df['last_updated'] = pd.to_datetime(df['last_updated'])
    
    logger.info(f"Cleaned {len(df)} accounts")
    return df


def clean_all_data(data):
    """
    Clean all datasets in the data dictionary.
    
    Args:
        data (dict): Dictionary containing raw data
        
    Returns:
        dict: Dictionary containing cleaned data
    """
    logger.info("Starting data cleaning...")
    
    cleaned = data.copy()
    
    if 'accounts' in cleaned and not cleaned['accounts'].empty:
        cleaned['accounts'] = clean_accounts(cleaned['accounts'])
    
    if 'orders' in cleaned and not cleaned['orders'].empty:
        cleaned['orders'] = clean_orders(cleaned['orders'])
    
    if 'positions' in cleaned and not cleaned['positions'].empty:
        cleaned['positions'] = clean_positions(cleaned['positions'])
    
    logger.info("Data cleaning complete")
    return cleaned
