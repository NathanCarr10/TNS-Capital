"""
Extract module: Load trading data from PostgreSQL.
"""

import os
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor
from dotenv import load_dotenv
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()


def get_db_connection():
    """
    Establish a connection to the PostgreSQL database.
    
    Returns:
        psycopg2 connection object or None if connection fails
    """
    try:
        conn = psycopg2.connect(
            host=os.getenv('DB_HOST', 'localhost'),
            port=os.getenv('DB_PORT', '5432'),
            database=os.getenv('DB_NAME', 'tns_capital'),
            user=os.getenv('DB_USER', 'tns_app'),
            password=os.getenv('DB_PASSWORD')
        )
        logger.info("Successfully connected to PostgreSQL database")
        return conn
    except psycopg2.Error as e:
        logger.error(f"Failed to connect to database: {e}")
        return None


def extract_accounts():
    """Extract accounts data from database."""
    conn = get_db_connection()
    if conn is None:
        return pd.DataFrame()
    
    try:
        df = pd.read_sql_query("SELECT * FROM accounts;", conn)
        logger.info(f"Extracted {len(df)} accounts")
        return df
    finally:
        conn.close()


def extract_instruments():
    """Extract instruments data from database."""
    conn = get_db_connection()
    if conn is None:
        return pd.DataFrame()
    
    try:
        df = pd.read_sql_query("SELECT * FROM instruments;", conn)
        logger.info(f"Extracted {len(df)} instruments")
        return df
    finally:
        conn.close()


def extract_orders():
    """Extract orders data from database."""
    conn = get_db_connection()
    if conn is None:
        return pd.DataFrame()
    
    try:
        df = pd.read_sql_query("SELECT * FROM orders;", conn)
        logger.info(f"Extracted {len(df)} orders")
        return df
    finally:
        conn.close()


def extract_positions():
    """Extract positions data from database."""
    conn = get_db_connection()
    if conn is None:
        return pd.DataFrame()
    
    try:
        df = pd.read_sql_query("SELECT * FROM positions;", conn)
        logger.info(f"Extracted {len(df)} positions")
        return df
    finally:
        conn.close()


def extract_all_data():
    """
    Extract all relevant trading data from the database.
    
    Returns:
        dict: Dictionary containing all extracted dataframes
    """
    logger.info("Starting data extraction...")
    
    data = {
        'accounts': extract_accounts(),
        'instruments': extract_instruments(),
        'orders': extract_orders(),
        'positions': extract_positions()
    }
    
    logger.info("Data extraction complete")
    return data


if __name__ == "__main__":
    # Test extraction
    data = extract_all_data()
    for key, df in data.items():
        print(f"\n{key.upper()}: {len(df)} rows")
        print(df.head())
