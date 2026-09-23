"""Snowflake Database Client"""

import snowflake.connector
from snowflake.connector.pandas_tools import write_pandas, pd_writer
from contextlib import contextmanager
from typing import List, Dict, Any, Generator
import pandas as pd

from etl.config import SnowflakeConfig
from etl.logging_config import get_logger

logger = get_logger(__name__)


class SnowflakeClient:
    """Manages Snowflake connections and operations"""
    
    def __init__(self):
        """Initialize Snowflake client with configuration"""
        self.config = SnowflakeConfig()
        self.connection = None
    
    def connect(self):
        """Establish connection to Snowflake"""
        try:
            self.connection = snowflake.connector.connect(**self.config.get_connection_params())
            logger.info(f"Connected to Snowflake: {self.config.account}/{self.config.database}/{self.config.schema}")
        except Exception as e:
            logger.error(f"Failed to connect to Snowflake: {str(e)}")
            raise
    
    def disconnect(self):
        """Close Snowflake connection"""
        if self.connection:
            self.connection.close()
            logger.info("Disconnected from Snowflake")
    
    def __enter__(self):
        """Context manager entry"""
        self.connect()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit"""
        self.disconnect()
    
    @contextmanager
    def cursor(self) -> Generator:
        """Context manager for database cursor
        
        Yields:
            Snowflake cursor
        """
        cur = self.connection.cursor()
        try:
            yield cur
        finally:
            cur.close()
    
    def execute_query(self, query: str) -> List[Dict[str, Any]]:
        """Execute a SELECT query and return results
        
        Args:
            query: SQL query to execute
        
        Returns:
            List of rows as dictionaries
        """
        try:
            with self.cursor() as cur:
                cur.execute(query)
                # Get column names
                cols = [desc[0] for desc in cur.description]
                # Fetch all results
                rows = cur.fetchall()
                results = [dict(zip(cols, row)) for row in rows]
                logger.debug(f"Query returned {len(results)} rows")
                return results
        except Exception as e:
            logger.error(f"Query execution failed: {str(e)}")
            raise
    
    def execute_update(self, query: str) -> int:
        """Execute an INSERT, UPDATE, DELETE, or MERGE query
        
        Args:
            query: SQL query to execute
        
        Returns:
            Number of rows affected
        """
        try:
            with self.cursor() as cur:
                cur.execute(query)
                # Snowflake returns rowcount for DML operations
                rows_affected = cur.rowcount if hasattr(cur, 'rowcount') else 0
                logger.debug(f"Query affected {rows_affected} rows")
                return rows_affected
        except Exception as e:
            logger.error(f"Update query failed: {str(e)}")
            raise
    
    def insert_dataframe(self, df: pd.DataFrame, table_name: str, if_exists: str = "append"):
        """Insert DataFrame into Snowflake table
        
        Args:
            df: pandas DataFrame to insert
            table_name: Target table name
            if_exists: 'append', 'replace', or 'fail'
        
        Returns:
            Number of rows inserted
        """
        try:
            success, nchunks, nrows, _ = write_pandas(
                self.connection,
                df,
                table_name.upper(),
                if_exists=if_exists,
                parallel=4
            )
            if success:
                logger.info(f"Inserted {nrows} rows into {table_name} ({nchunks} chunks)")
                return nrows
            else:
                logger.warning(f"Insert to {table_name} did not complete successfully")
                return 0
        except Exception as e:
            logger.error(f"Failed to insert DataFrame to {table_name}: {str(e)}")
            raise
    
    def upsert_dataframe(self, df: pd.DataFrame, table_name: str, key_columns: List[str]):
        """Upsert DataFrame into Snowflake table (update if exists, insert if new)
        
        Args:
            df: pandas DataFrame to upsert
            table_name: Target table name
            key_columns: List of column names to use as merge key
        """
        try:
            # Create temporary stage table
            temp_table = f"{table_name}_TEMP"
            
            # Insert into temp table
            self.insert_dataframe(df, temp_table, if_exists="replace")
            
            # Build MERGE statement
            key_condition = " AND ".join([f"t.{col} = s.{col}" for col in key_columns])
            update_cols = [col for col in df.columns if col not in key_columns]
            update_statement = ", ".join([f"t.{col} = s.{col}" for col in update_cols])
            insert_cols = ", ".join(df.columns)
            insert_values = ", ".join([f"s.{col}" for col in df.columns])
            
            merge_query = f"""
                MERGE INTO {table_name.upper()} t
                USING {temp_table} s
                ON {key_condition}
                WHEN MATCHED THEN
                    UPDATE SET {update_statement}
                WHEN NOT MATCHED THEN
                    INSERT ({insert_cols})
                    VALUES ({insert_values})
            """
            
            self.execute_update(merge_query)
            logger.info(f"Upserted {len(df)} rows into {table_name}")
            
            # Drop temp table
            self.execute_update(f"DROP TABLE {temp_table}")
            
        except Exception as e:
            logger.error(f"Failed to upsert DataFrame to {table_name}: {str(e)}")
            raise
    
    def table_exists(self, table_name: str) -> bool:
        """Check if a table exists in Snowflake
        
        Args:
            table_name: Name of the table to check
        
        Returns:
            True if table exists, False otherwise
        """
        query = f"""
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables 
                WHERE table_schema = '{self.config.schema.upper()}'
                AND table_name = '{table_name.upper()}'
            )
        """
        try:
            result = self.execute_query(query)
            exists = result[0][0] if result else False
            logger.debug(f"Table {table_name} exists: {exists}")
            return exists
        except Exception as e:
            logger.error(f"Failed to check table existence: {str(e)}")
            raise
    
    def get_row_count(self, table_name: str) -> int:
        """Get row count for a table
        
        Args:
            table_name: Name of the table
        
        Returns:
            Number of rows in table
        """
        query = f"SELECT COUNT(*) as count FROM {table_name.upper()}"
        try:
            result = self.execute_query(query)
            count = result[0]['COUNT'] if result else 0
            logger.debug(f"Table {table_name} has {count} rows")
            return count
        except Exception as e:
            logger.error(f"Failed to get row count for {table_name}: {str(e)}")
            raise
    
    def get_max_timestamp(self, table_name: str, timestamp_column: str) -> str:
        """Get maximum timestamp from a table column (for incremental loading)
        
        Args:
            table_name: Name of the table
            timestamp_column: Name of the timestamp column
        
        Returns:
            Maximum timestamp as string, or None if table is empty
        """
        query = f"SELECT MAX({timestamp_column}) as max_timestamp FROM {table_name.upper()}"
        try:
            result = self.execute_query(query)
            max_ts = result[0]['MAX_TIMESTAMP'] if result and result[0]['MAX_TIMESTAMP'] else None
            logger.debug(f"Max timestamp in {table_name}.{timestamp_column}: {max_ts}")
            return max_ts
        except Exception as e:
            logger.error(f"Failed to get max timestamp: {str(e)}")
            raise
