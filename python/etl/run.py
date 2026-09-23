#!/usr/bin/env python3
"""ETL Pipeline Entry Point

Usage:
    python run.py                 # Run pipeline normally
    python run.py --dry-run       # Run without loading to Snowflake
    python run.py --debug         # Run with debug logging
    python run.py --help          # Show help
"""

import sys
import argparse
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from etl.logging_config import get_logger, setup_logging
from etl.config import ETLConfig
from etl.pipeline import ETLPipeline

logger = get_logger(__name__)


def main():
    """Main entry point"""
    
    # Parse command line arguments
    parser = argparse.ArgumentParser(
        description="TNS Capital ETL Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python run.py                 # Run pipeline normally
  python run.py --dry-run       # Run without loading to Snowflake
  python run.py --debug         # Run with debug logging
        """
    )
    
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run pipeline without loading to Snowflake (extract and transform only)"
    )
    
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Enable debug logging"
    )
    
    parser.add_argument(
        "--version",
        action="version",
        version="%(prog)s 0.1.0"
    )
    
    args = parser.parse_args()
    
    # Set up logging (already done on import, but can enhance here)
    logger.info(f"ETL Pipeline started with arguments: {vars(args)}")
    
    try:
        # Create and run pipeline
        pipeline = ETLPipeline(dry_run=args.dry_run)
        stats = pipeline.run()
        
        # Print final stats
        logger.info("\nFinal Statistics:")
        for key, value in stats.items():
            logger.info(f"  {key}: {value}")
        
        sys.exit(0)
        
    except KeyboardInterrupt:
        logger.warning("Pipeline interrupted by user")
        sys.exit(130)
    except Exception as e:
        logger.error(f"Pipeline failed: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()
