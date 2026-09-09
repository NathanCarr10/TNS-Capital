"""
Insights module: Calculate business metrics and key insights.
"""

import pandas as pd
import logging

logger = logging.getLogger(__name__)


def trade_volume_analysis(orders_df, accounts_df):
    """
    Analyze trade volume by account and instrument.
    
    Args:
        orders_df (pd.DataFrame): Cleaned orders data
        accounts_df (pd.DataFrame): Cleaned accounts data
        
    Returns:
        dict: Trade volume metrics
    """
    if orders_df.empty:
        return {}
    
    # Volume by account
    volume_by_account = (
        orders_df.groupby('account_id')
        .agg({
            'order_value': 'sum',
            'quantity': 'sum',
            'id': 'count'
        })
        .rename(columns={'id': 'order_count'})
        .sort_values('order_value', ascending=False)
    )
    
    # Volume by instrument
    volume_by_instrument = (
        orders_df.groupby('symbol')
        .agg({
            'order_value': 'sum',
            'quantity': 'sum',
            'id': 'count'
        })
        .rename(columns={'id': 'order_count'})
        .sort_values('order_value', ascending=False)
    )
    
    logger.info("Trade volume analysis complete")
    return {
        'volume_by_account': volume_by_account,
        'volume_by_instrument': volume_by_instrument,
        'total_trade_value': orders_df['order_value'].sum(),
        'total_orders': len(orders_df)
    }


def fill_rate_analysis(orders_df):
    """
    Calculate order fill rates.
    
    Args:
        orders_df (pd.DataFrame): Cleaned orders data
        
    Returns:
        dict: Fill rate metrics
    """
    if orders_df.empty:
        return {}
    
    total_orders = len(orders_df)
    filled_orders = len(orders_df[orders_df['status'] == 'FILLED'])
    rejected_orders = len(orders_df[orders_df['status'] == 'REJECTED'])
    cancelled_orders = len(orders_df[orders_df['status'] == 'CANCELLED'])
    new_orders = len(orders_df[orders_df['status'] == 'NEW'])
    
    fill_rate = (filled_orders / total_orders * 100) if total_orders > 0 else 0
    
    logger.info(f"Fill rate: {fill_rate:.2f}%")
    return {
        'total_orders': total_orders,
        'filled_orders': filled_orders,
        'rejected_orders': rejected_orders,
        'cancelled_orders': cancelled_orders,
        'new_orders': new_orders,
        'fill_rate_percent': round(fill_rate, 2)
    }


def account_activity_analysis(orders_df, accounts_df):
    """
    Identify most active accounts.
    
    Args:
        orders_df (pd.DataFrame): Cleaned orders data
        accounts_df (pd.DataFrame): Cleaned accounts data
        
    Returns:
        pd.DataFrame: Account activity metrics
    """
    if orders_df.empty or accounts_df.empty:
        return pd.DataFrame()
    
    activity = (
        orders_df.groupby('account_id')
        .agg({
            'order_value': ['sum', 'mean', 'count'],
            'quantity': 'sum'
        })
        .reset_index()
    )
    
    activity.columns = ['account_id', 'total_value', 'avg_order_value', 'order_count', 'total_quantity']
    activity = activity.merge(accounts_df[['id', 'account_id', 'holder_name']], on='account_id', how='left')
    activity = activity.sort_values('total_value', ascending=False)
    
    logger.info("Account activity analysis complete")
    return activity


def instrument_exposure_analysis(positions_df):
    """
    Analyze exposure by instrument.
    
    Args:
        positions_df (pd.DataFrame): Cleaned positions data
        
    Returns:
        pd.DataFrame: Instrument exposure metrics
    """
    if positions_df.empty:
        return pd.DataFrame()
    
    exposure = (
        positions_df.groupby('symbol')
        .agg({
            'quantity': 'sum',
            'position_value': 'sum',
            'account_id': 'count'
        })
        .reset_index()
        .rename(columns={'account_id': 'num_holders'})
        .sort_values('position_value', ascending=False)
    )
    
    logger.info("Instrument exposure analysis complete")
    return exposure


def generate_all_insights(data):
    """
    Generate all business insights from cleaned data.
    
    Args:
        data (dict): Dictionary containing cleaned data
        
    Returns:
        dict: Dictionary containing all insights
    """
    logger.info("Generating business insights...")
    
    orders = data.get('orders', pd.DataFrame())
    accounts = data.get('accounts', pd.DataFrame())
    positions = data.get('positions', pd.DataFrame())
    
    insights = {
        'trade_volume': trade_volume_analysis(orders, accounts),
        'fill_rate': fill_rate_analysis(orders),
        'account_activity': account_activity_analysis(orders, accounts),
        'instrument_exposure': instrument_exposure_analysis(positions)
    }
    
    logger.info("Insights generation complete")
    return insights
