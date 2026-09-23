"""ETL Pipeline Orchestrator"""

import pandas as pd
from datetime import datetime
from typing import Dict, Any

from etl.logging_config import get_logger
from etl.extractors.accounts_extractor import AccountsExtractor
from etl.extractors.instruments_extractor import InstrumentsExtractor
from etl.extractors.orders_extractor import OrdersExtractor
from etl.transformers.dim_account_transformer import DimAccountTransformer
from etl.transformers.dim_instrument_transformer import DimInstrumentTransformer
from etl.transformers.dim_date_transformer import DimDateTransformer
from etl.transformers.fact_trades_transformer import FactTradesTransformer
from etl.loaders.dim_loader import DimLoader
from etl.loaders.fact_loader import FactLoader
from etl.generators.order_generator import OrderGenerator
from etl.utils.snowflake_client import SnowflakeClient

logger = get_logger(__name__)


class ETLPipeline:
    """Main ETL Pipeline Orchestrator"""
    
    def __init__(self, dry_run: bool = False):
        """Initialize ETL pipeline
        
        Args:
            dry_run: If True, only perform extractions and transformations without loading
        """
        self.dry_run = dry_run
        self.start_time = None
        self.end_time = None
        self.stats = {}
    
    def run(self) -> Dict[str, Any]:
        """Execute full ETL pipeline
        
        Returns:
            Dictionary with pipeline statistics
        """
        self.start_time = datetime.now()
        logger.info("=" * 60)
        logger.info(f"Starting ETL Pipeline (Dry Run: {self.dry_run})")
        logger.info("=" * 60)
        
        try:
            # Phase 1: Extract
            logger.info("\n[Phase 1] Extracting data from PostgreSQL...")
            accounts_df, instruments_df, orders_df = self._extract_phase()
            
            # Phase 2: Generate Synthetic Data
            logger.info("\n[Phase 2] Generating synthetic orders...")
            synthetic_orders_df = self._generate_phase(accounts_df, instruments_df)
            
            # Combine real and synthetic orders
            combined_orders_df = pd.concat([orders_df, synthetic_orders_df], ignore_index=True)
            logger.info(f"Combined {len(orders_df)} real orders with {len(synthetic_orders_df)} synthetic orders")
            
            # Phase 3: Transform dimensions (not facts yet)
            logger.info("\n[Phase 3] Transforming dimensions to dimensional model...")
            dim_account_df, dim_instrument_df, dim_date_df = self._transform_dimensions_phase(
                accounts_df, instruments_df, combined_orders_df
            )
            
            # Phase 4: Load dimensions (if not dry run)
            if not self.dry_run:
                logger.info("\n[Phase 4] Loading dimensions to Snowflake...")
                self._load_dimensions_phase(dim_account_df, dim_instrument_df, dim_date_df)
                
                # Phase 5: Fetch dimension lookups from Snowflake and transform facts
                logger.info("\n[Phase 5] Fetching dimension lookups and transforming facts...")
                dim_account_lookup, dim_instrument_lookup, dim_date_lookup = self._fetch_dimension_lookups()
                fact_trades_df = self._transform_facts_phase(combined_orders_df, dim_account_lookup, dim_instrument_lookup, dim_date_lookup)
                
                # Phase 6: Load facts
                logger.info("\n[Phase 6] Loading facts to Snowflake...")
                self._load_facts_phase(fact_trades_df)
            else:
                logger.info("\n[Phase 4-6] Skipping load and fact transformation (dry run)")
            
            # Finalize
            self.end_time = datetime.now()
            duration = (self.end_time - self.start_time).total_seconds()
            
            logger.info("\n" + "=" * 60)
            logger.info("ETL Pipeline Completed Successfully")
            logger.info("=" * 60)
            logger.info(f"Duration: {duration:.2f} seconds")
            logger.info(f"Statistics: {self.stats}")
            
            return self.stats
            
        except Exception as e:
            self.end_time = datetime.now()
            duration = (self.end_time - self.start_time).total_seconds()
            logger.error(f"\nETL Pipeline Failed after {duration:.2f} seconds")
            logger.error(f"Error: {str(e)}")
            raise
    
    def _extract_phase(self):
        """Extract data from PostgreSQL
        
        Returns:
            Tuple of (accounts_df, instruments_df, orders_df)
        """
        try:
            # Extract accounts
            logger.info("  Extracting accounts...")
            accounts_extractor = AccountsExtractor()
            accounts_df = accounts_extractor.extract()
            self.stats['accounts_extracted'] = len(accounts_df)
            logger.info(f"  ✓ Extracted {len(accounts_df)} accounts")
            
            # Extract instruments
            logger.info("  Extracting instruments...")
            instruments_extractor = InstrumentsExtractor()
            instruments_df = instruments_extractor.extract()
            self.stats['instruments_extracted'] = len(instruments_df)
            logger.info(f"  ✓ Extracted {len(instruments_df)} instruments")
            
            # Extract orders
            logger.info("  Extracting orders...")
            orders_extractor = OrdersExtractor()
            orders_df = orders_extractor.extract()
            self.stats['orders_extracted'] = len(orders_df)
            logger.info(f"  ✓ Extracted {len(orders_df)} orders")
            
            return accounts_df, instruments_df, orders_df
            
        except Exception as e:
            logger.error(f"Extraction phase failed: {str(e)}")
            raise
    
    def _generate_phase(self, accounts_df: pd.DataFrame, instruments_df: pd.DataFrame) -> pd.DataFrame:
        """Generate synthetic orders
        
        Returns:
            DataFrame with synthetic orders
        """
        try:
            generator = OrderGenerator()
            generator.set_account_ids(accounts_df['account_id'].tolist())
            generator.set_symbols(instruments_df['symbol'].tolist())
            synthetic_orders_df = generator.generate()
            
            # Validate synthetic orders
            is_valid = generator.validate_orders(accounts_df, instruments_df)
            if not is_valid:
                logger.warning("Synthetic orders validation found issues (see above)")
            
            self.stats['synthetic_orders_generated'] = len(synthetic_orders_df)
            logger.info(f"✓ Generated {len(synthetic_orders_df)} synthetic orders")
            
            return synthetic_orders_df
            
        except Exception as e:
            logger.error(f"Synthetic data generation failed: {str(e)}")
            raise
    
    def _transform_dimensions_phase(self, accounts_df: pd.DataFrame, instruments_df: pd.DataFrame, orders_df: pd.DataFrame):
        """Transform data to dimensional model (dimensions only, not facts)
        
        Returns:
            Tuple of (dim_account_df, dim_instrument_df, dim_date_df)
        """
        try:
            # Transform accounts to DIM_ACCOUNT
            logger.info("  Transforming accounts...")
            dim_account_transformer = DimAccountTransformer()
            dim_account_df = dim_account_transformer.transform(accounts_df)
            self.stats['dim_account_transformed'] = len(dim_account_df)
            logger.info(f"  ✓ Transformed {len(dim_account_df)} dimension accounts")
            
            # Transform instruments to DIM_INSTRUMENT
            logger.info("  Transforming instruments...")
            dim_instrument_transformer = DimInstrumentTransformer()
            dim_instrument_df = dim_instrument_transformer.transform(instruments_df)
            self.stats['dim_instrument_transformed'] = len(dim_instrument_df)
            logger.info(f"  ✓ Transformed {len(dim_instrument_df)} dimension instruments")
            
            # Transform dates to DIM_DATE
            logger.info("  Transforming dates...")
            dim_date_transformer = DimDateTransformer()
            dim_date_df = dim_date_transformer.transform(orders_df)
            self.stats['dim_date_transformed'] = len(dim_date_df)
            logger.info(f"  ✓ Transformed {len(dim_date_df)} dimension dates")
            
            return dim_account_df, dim_instrument_df, dim_date_df
            
        except Exception as e:
            logger.error(f"Dimension transformation phase failed: {str(e)}")
            raise
    
    def _load_dimensions_phase(self, dim_account_df: pd.DataFrame, dim_instrument_df: pd.DataFrame, 
                              dim_date_df: pd.DataFrame):
        """Load dimension data to Snowflake
        
        Args:
            dim_account_df: Dimension account data
            dim_instrument_df: Dimension instrument data
            dim_date_df: Dimension date data
        """
        try:
            dim_loader = DimLoader()
            
            # Load dimensions with retry logic
            logger.info("  Loading dimensions...")
            self._load_with_retry(lambda: dim_loader.load_dim_account(dim_account_df), "DIM_ACCOUNT")
            self._load_with_retry(lambda: dim_loader.load_dim_instrument(dim_instrument_df), "DIM_INSTRUMENT")
            self._load_with_retry(lambda: dim_loader.load_dim_date(dim_date_df), "DIM_DATE")
            logger.info("  ✓ Dimensions loaded")
            
            self.stats['dim_account_loaded'] = len(dim_account_df)
            self.stats['dim_instrument_loaded'] = len(dim_instrument_df)
            self.stats['dim_date_loaded'] = len(dim_date_df)
            
        except Exception as e:
            logger.error(f"Dimension load phase failed: {str(e)}")
            raise
    
    def _transform_facts_phase(self, orders_df: pd.DataFrame, dim_account: pd.DataFrame, 
                               dim_instrument: pd.DataFrame, dim_date: pd.DataFrame):
        """Transform orders to FACT_TRADES using dimension lookups from Snowflake
        
        Args:
            orders_df: Combined real and synthetic orders
            dim_account: Account dimension lookup (with ACCOUNT_KEY)
            dim_instrument: Instrument dimension lookup (with INSTRUMENT_KEY)
            dim_date: Date dimension lookup (with DATE_KEY)
        
        Returns:
            DataFrame with fact trades
        """
        try:
            logger.info("  Transforming orders to facts...")
            fact_trades_transformer = FactTradesTransformer()
            fact_trades_transformer.set_dimensions(dim_account, dim_instrument, dim_date)
            fact_trades_df = fact_trades_transformer.transform(orders_df)
            self.stats['fact_trades_transformed'] = len(fact_trades_df)
            logger.info(f"  ✓ Transformed {len(fact_trades_df)} fact trades")
            
            return fact_trades_df
            
        except Exception as e:
            logger.error(f"Fact transformation phase failed: {str(e)}")
            raise
    
    def _load_facts_phase(self, fact_trades_df: pd.DataFrame):
        """Load fact trades data to Snowflake
        
        Args:
            fact_trades_df: Fact trades data
        """
        try:
            fact_loader = FactLoader()
            
            # Load facts with retry logic
            logger.info("  Loading facts...")
            self._load_with_retry(lambda: fact_loader.load_fact_trades(fact_trades_df), "FACT_TRADES")
            logger.info("  ✓ Facts loaded")
            
            self.stats['fact_trades_loaded'] = len(fact_trades_df)
            
        except Exception as e:
            logger.error(f"Fact load phase failed: {str(e)}")
            raise
    
    def _load_with_retry(self, load_func, table_name: str, max_retries: int = 3):
        """Execute load function with retry logic
        
        Args:
            load_func: Function to execute
            table_name: Name of table being loaded
            max_retries: Maximum number of retry attempts
        """
        for attempt in range(1, max_retries + 1):
            try:
                load_func()
                break  # Success, exit retry loop
            except Exception as e:
                if attempt < max_retries:
                    logger.warning(f"Attempt {attempt} failed for {table_name}: {str(e)}. Retrying...")
                else:
                    logger.error(f"All {max_retries} attempts failed for {table_name}")
                    raise
                
    def _fetch_dimension_lookups(self):
        with SnowflakeClient() as client:
            dim_account = pd.DataFrame(client.execute_query(
                "SELECT ACCOUNT_KEY, ACCOUNT_ID FROM DIM_ACCOUNT"
            ))
            dim_instrument = pd.DataFrame(client.execute_query(
                "SELECT INSTRUMENT_KEY, SYMBOL FROM DIM_INSTRUMENT"
            ))
            dim_date = pd.DataFrame(client.execute_query(
                "SELECT DATE_KEY, FULL_DATE FROM DIM_DATE"
            ))
        return dim_account, dim_instrument, dim_date
