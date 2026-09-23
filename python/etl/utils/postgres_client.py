"""PostgreSQL Database Client"""

import psycopg2
from psycopg2.extras import RealDictCursor
from contextlib import contextmanager
from typing import List, Dict, Any, Generator
import pandas as pd

from etl.config import PostgreSQLConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class PostgreSQLClient:
    """Manages PostgreSQL connections and queries"""
    
    def __init__(self):
        """Initialize PostgreSQL client with configuration"""
        self.config = PostgreSQLConfig()
        self.connection = None
    
    def connect(self):
        """Establish connection to PostgreSQL database"""
        try:
            self.connection = psycopg2.connect(**self.config.get_psycopg2_params())
            logger.info(f"Connected to PostgreSQL: {self.config.host}:{self.config.port}/{self.config.database}")
        except psycopg2.Error as e:
            logger.error(f"Failed to connect to PostgreSQL: {str(e)}")
            raise
    
    def disconnect(self):
        """Close PostgreSQL connection"""
        if self.connection:
            self.connection.close()
            logger.info("Disconnected from PostgreSQL")
    
    def __enter__(self):
        """Context manager entry"""
        self.connect()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit"""
        self.disconnect()
    
    @contextmanager
    def cursor(self, dict_cursor: bool = False) -> Generator:
        """Context manager for database cursor
        
        Args:
            dict_cursor: If True, returns results as dictionaries instead of tuples
        
        Yields:
            psycopg2 cursor
        """
        cursor_class = RealDictCursor if dict_cursor else None
        cur = self.connection.cursor(cursor_factory=cursor_class)
        try:
            yield cur
        finally:
            cur.close()
    
    def execute_query(self, query: str, params: tuple = None) -> List[Dict[str, Any]]:
        """Execute a SELECT query and return results as list of dictionaries
        
        Args:
            query: SQL query to execute
            params: Query parameters (for parameterized queries)
        
        Returns:
            List of rows as dictionaries
        """
        try:
            with self.cursor(dict_cursor=True) as cur:
                cur.execute(query, params)
                results = cur.fetchall()
                logger.debug(f"Query returned {len(results)} rows")
                return results
        except psycopg2.Error as e:
            logger.error(f"Query execution failed: {str(e)}")
            raise
    
    def execute_update(self, query: str, params: tuple = None) -> int:
        """Execute an INSERT, UPDATE, or DELETE query
        
        Args:
            query: SQL query to execute
            params: Query parameters
        
        Returns:
            Number of rows affected
        """
        try:
            with self.cursor() as cur:
                cur.execute(query, params)
                self.connection.commit()
                rows_affected = cur.rowcount
                logger.debug(f"Query affected {rows_affected} rows")
                return rows_affected
        except psycopg2.Error as e:
            self.connection.rollback()
            logger.error(f"Update query failed: {str(e)}")
            raise
    
    def fetch_table_as_dataframe(self, table_name: str, limit: int = None) -> pd.DataFrame:
        """Fetch entire table as pandas DataFrame
        
        Args:
            table_name: Name of the table to fetch
            limit: Optional row limit
        
        Returns:
            DataFrame with table data
        """
        query = f"SELECT * FROM {table_name}"
        if limit:
            query += f" LIMIT {limit}"
        
        try:
            with self.connection.cursor() as cur:
                cur.execute(query)
                cols = [desc[0] for desc in cur.description]
                rows = cur.fetchall()
                df = pd.DataFrame(rows, columns=cols)
                logger.info(f"Fetched {len(df)} rows from {table_name}")
                return df
        except psycopg2.Error as e:
            logger.error(f"Failed to fetch table {table_name}: {str(e)}")
            raise
    
    def table_exists(self, table_name: str) -> bool:
        """Check if a table exists in the database
        
        Args:
            table_name: Name of the table to check
        
        Returns:
            True if table exists, False otherwise
        """
        query = """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables 
                WHERE table_name = %s
            )
        """
        try:
            with self.cursor() as cur:
                cur.execute(query, (table_name,))
                return cur.fetchone()[0]
        except psycopg2.Error as e:
            logger.error(f"Failed to check table existence: {str(e)}")
            raise
    
    def get_row_count(self, table_name: str) -> int:
        """Get row count for a table
        
        Args:
            table_name: Name of the table
        
        Returns:
            Number of rows in table
        """
        query = f"SELECT COUNT(*) as count FROM {table_name}"
        try:
            result = self.execute_query(query)
            count = result[0]['count'] if result else 0
            logger.debug(f"Table {table_name} has {count} rows")
            return count
        except psycopg2.Error as e:
            logger.error(f"Failed to get row count for {table_name}: {str(e)}")
            raise
