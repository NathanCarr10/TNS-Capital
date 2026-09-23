"""Logging Configuration for ETL Pipeline"""

import logging
import logging.handlers
from pathlib import Path
from datetime import datetime
from etl.config import ETLConfig


def setup_logging():
    """Configure structured logging with file and console handlers"""
    
    # Ensure logs directory exists
    ETLConfig.ensure_logs_dir()
    
    # Create logger
    logger = logging.getLogger("etl")
    logger.setLevel(ETLConfig.log_level.upper())
    
    # Create formatters
    detailed_formatter = logging.Formatter(
        fmt="%(asctime)s - %(name)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    
    simple_formatter = logging.Formatter(
        fmt="%(asctime)s - %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    
    # File handler - rotating daily log files
    log_file = ETLConfig.logs_dir / f"etl-{datetime.now().strftime('%Y-%m-%d')}.log"
    file_handler = logging.FileHandler(log_file)
    file_handler.setLevel(ETLConfig.log_level.upper())
    file_handler.setFormatter(detailed_formatter)
    logger.addHandler(file_handler)
    
    # Console handler for INFO and above
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(simple_formatter)
    logger.addHandler(console_handler)
    
    return logger


# Initialize logger on module import
logger = setup_logging()


def get_logger(name: str = None):
    """Get a logger instance for a specific module
    
    Args:
        name: Module name for the logger (e.g., __name__)
    
    Returns:
        logging.Logger: Configured logger instance
    """
    if name:
        return logging.getLogger(f"etl.{name}")
    return logging.getLogger("etl")
