"""
Charts module: Generate visualizations for business insights.
"""

import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import logging

logger = logging.getLogger(__name__)

# Set style
sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (12, 6)


def plot_trade_volume_by_account(trade_volume_dict):
    """
    Plot trade volume by account.
    
    Args:
        trade_volume_dict (dict): Trade volume metrics from insights
        
    Returns:
        matplotlib.figure.Figure: The figure object
    """
    if 'volume_by_account' not in trade_volume_dict:
        return None
    
    df = trade_volume_dict['volume_by_account'].reset_index()
    
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.bar(df.index, df['order_value'], color='steelblue')
    ax.set_xlabel('Account ID')
    ax.set_ylabel('Total Order Value (USD)')
    ax.set_title('Trade Volume by Account')
    plt.xticks(rotation=45)
    plt.tight_layout()
    
    logger.info("Generated trade volume by account chart")
    return fig


def plot_fill_rate(fill_rate_dict):
    """
    Plot order fill rate breakdown.
    
    Args:
        fill_rate_dict (dict): Fill rate metrics from insights
        
    Returns:
        matplotlib.figure.Figure: The figure object
    """
    if not fill_rate_dict:
        return None
    
    statuses = ['filled_orders', 'rejected_orders', 'cancelled_orders', 'new_orders']
    values = [fill_rate_dict.get(status, 0) for status in statuses]
    labels = ['Filled', 'Rejected', 'Cancelled', 'New']
    colors = ['#2ecc71', '#e74c3c', '#f39c12', '#3498db']
    
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.pie(values, labels=labels, autopct='%1.1f%%', colors=colors, startangle=90)
    ax.set_title(f"Order Status Breakdown (Fill Rate: {fill_rate_dict.get('fill_rate_percent', 0)}%)")
    
    logger.info("Generated fill rate chart")
    return fig


def plot_instrument_exposure(instrument_exposure_df):
    """
    Plot instrument exposure distribution.
    
    Args:
        instrument_exposure_df (pd.DataFrame): Instrument exposure data
        
    Returns:
        matplotlib.figure.Figure: The figure object
    """
    if instrument_exposure_df.empty:
        return None
    
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.barh(instrument_exposure_df['symbol'], instrument_exposure_df['position_value'], color='coral')
    ax.set_xlabel('Total Position Value (USD)')
    ax.set_ylabel('Instrument')
    ax.set_title('Exposure by Instrument')
    plt.tight_layout()
    
    logger.info("Generated instrument exposure chart")
    return fig


def plot_account_activity(account_activity_df):
    """
    Plot account activity metrics.
    
    Args:
        account_activity_df (pd.DataFrame): Account activity data
        
    Returns:
        matplotlib.figure.Figure: The figure object
    """
    if account_activity_df.empty:
        return None
    
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    
    # Total value by account
    ax1.barh(account_activity_df['holder_name'], account_activity_df['total_value'], color='skyblue')
    ax1.set_xlabel('Total Trade Value (USD)')
    ax1.set_title('Total Trading Value by Account')
    
    # Order count by account
    ax2.barh(account_activity_df['holder_name'], account_activity_df['order_count'], color='lightgreen')
    ax2.set_xlabel('Number of Orders')
    ax2.set_title('Order Count by Account')
    
    plt.tight_layout()
    logger.info("Generated account activity chart")
    return fig


def generate_all_charts(insights):
    """
    Generate all visualization charts.
    
    Args:
        insights (dict): Dictionary containing all insights
        
    Returns:
        dict: Dictionary containing all figures
    """
    logger.info("Generating charts...")
    
    charts = {
        'trade_volume': plot_trade_volume_by_account(insights.get('trade_volume', {})),
        'fill_rate': plot_fill_rate(insights.get('fill_rate', {})),
        'instrument_exposure': plot_instrument_exposure(insights.get('instrument_exposure', pd.DataFrame())),
        'account_activity': plot_account_activity(insights.get('account_activity', pd.DataFrame()))
    }
    
    logger.info("Chart generation complete")
    return charts
