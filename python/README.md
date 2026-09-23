# TNS Capital ETL Pipeline

Python ETL pipeline for extracting trading data from PostgreSQL, transforming to a dimensional model, and loading to Snowflake for analytics.

## Architecture

### Data Flow
```
PostgreSQL (Source)
    ├── accounts
    ├── instruments
    └── orders
         ↓
[EXTRACT] → [TRANSFORM] → [LOAD] → Snowflake (Analytics)
     ↓
Synthetic Data Generation
(50,000 test orders)
     ↓
Dimension Tables          Fact Table
├── DIM_ACCOUNT          └── FACT_TRADES
├── DIM_INSTRUMENT
└── DIM_DATE
```

### Components

#### Extractors (`etl/extractors/`)
Extract data from PostgreSQL source:
- `AccountsExtractor` - Extracts account records
- `InstrumentsExtractor` - Extracts instrument/security records
- `OrdersExtractor` - Extracts trading order records

#### Transformers (`etl/transformers/`)
Transform data to dimensional model format:
- `DimAccountTransformer` - Maps accounts to DIM_ACCOUNT
- `DimInstrumentTransformer` - Maps instruments to DIM_INSTRUMENT
- `DimDateTransformer` - Generates calendar DIM_DATE
- `FactTradesTransformer` - Maps orders to FACT_TRADES with key lookups

#### Loaders (`etl/loaders/`)
Load transformed data to Snowflake:
- `DimLoader` - Upserts dimension tables (handles updates)
- `FactLoader` - Inserts fact records with MERGE for idempotency

#### Generators (`etl/generators/`)
Generate synthetic test data:
- `OrderGenerator` - Creates 50,000 realistic synthetic orders for testing

#### Utilities (`etl/utils/`)
Database connectivity:
- `PostgreSQLClient` - PostgreSQL connection and query execution
- `SnowflakeClient` - Snowflake connection and bulk operations

### Configuration
Environment variables in `.env` file:
- PostgreSQL connection details
- Snowflake connection details
- ETL parameters (batch size, synthetic order count, lookback period)

## Installation

### 1. Install Python Dependencies

```bash
cd python
pip install -r requirements.txt
```

### 2. Configure Environment

Edit `python/.env` with your database credentials (already configured):

```dotenv
# PostgreSQL
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=tns_capital
POSTGRES_USER=tns-capital-db-user
POSTGRES_PASSWORD=<your-password>

# Snowflake
SNOWFLAKE_ACCOUNT=<your-account>
SNOWFLAKE_USER=<your-user>
SNOWFLAKE_PASSWORD=<your-password>
SNOWFLAKE_DATABASE=TNS_ANALYTICS
SNOWFLAKE_SCHEMA=TRADING
SNOWFLAKE_WAREHOUSE=ANALYTICS_WH

# ETL Configuration
BATCH_SIZE=1000
SYNTHETIC_ORDERS_COUNT=50000
HISTORICAL_PERIOD_YEARS=1
LOG_LEVEL=INFO
```

## Usage

### Run Full Pipeline

```bash
cd python
python etl/run.py
```

### Dry Run (Extract & Transform Only, No Load)

```bash
python etl/run.py --dry-run
```

### Debug Mode (Verbose Logging)

```bash
python etl/run.py --debug
```

### Help

```bash
python etl/run.py --help
```

## Pipeline Execution

### Phase 1: Extraction
- Extracts accounts, instruments, and orders from PostgreSQL
- Uses 1-year historical lookback for orders

### Phase 2: Synthetic Data Generation
- Generates 50,000 realistic test orders
- Uses real account IDs and symbols
- Assigns random attributes (side, quantity, price, timestamp)
- Includes idempotency keys to prevent duplicates

### Phase 3: Transformation
- Transforms accounts → DIM_ACCOUNT
- Transforms instruments → DIM_INSTRUMENT
- Generates calendar → DIM_DATE
- Transforms orders + synthetic orders → FACT_TRADES
- Performs dimensional lookups (maps to foreign keys)

### Phase 4: Load
- Upserts dimensions to Snowflake (insert new, update existing)
- Inserts facts to FACT_TRADES with MERGE for idempotency
- Handles retries (3 attempts per batch on failure)

## Output

### Logs
Generated in `python/logs/` directory:
- Daily log files: `etl-YYYY-MM-DD.log`
- Includes extraction counts, transformation statistics, load results

### Snowflake Tables
- **DIM_ACCOUNT** - Account dimension (account_key, account_id, holder_name, status, effective_date)
- **DIM_INSTRUMENT** - Instrument dimension (instrument_key, symbol, name, asset_class, currency)
- **DIM_DATE** - Date dimension (date_key, full_date, day, month, year, quarter)
- **FACT_TRADES** - Trading facts (account_key, instrument_key, date_key, side, quantity, price, status, created_on, idempotency_key)

## Testing

### Run Unit Tests

```bash
cd python
python -m pytest etl/tests/test_generators.py -v
```

### Validate Connections

```python
from etl.utils.postgres_client import PostgreSQLClient
from etl.utils.snowflake_client import SnowflakeClient

# Test PostgreSQL
with PostgreSQLClient() as pg:
    accounts = pg.get_row_count("accounts")
    print(f"Accounts in PostgreSQL: {accounts}")

# Test Snowflake
with SnowflakeClient() as sf:
    trades = sf.get_row_count("FACT_TRADES")
    print(f"Trades in Snowflake: {trades}")
```

## Scheduling

### Cron Job (Daily at 2 AM)

```bash
0 2 * * * cd /path/to/tns-capital/python && python etl/run.py
```

### Docker Entry Point

Add to `Dockerfile`:
```dockerfile
CMD ["python", "python/etl/run.py"]
```

### Jenkins Pipeline

Add to `Jenkinsfile`:
```groovy
stage('ETL Pipeline') {
    steps {
        sh 'cd python && python etl/run.py'
    }
}
```

## Troubleshooting

### Connection Issues

```bash
# Test PostgreSQL connection
python -c "from etl.utils.postgres_client import PostgreSQLClient; client = PostgreSQLClient(); client.connect()"

# Test Snowflake connection
python -c "from etl.utils.snowflake_client import SnowflakeClient; client = SnowflakeClient(); client.connect()"
```

### View Logs

```bash
tail -f python/logs/etl-*.log
```

### Debug Specific Step

```python
from etl.extractors.accounts_extractor import AccountsExtractor
from etl.logging_config import get_logger

logger = get_logger(__name__)
extractor = AccountsExtractor()
accounts = extractor.extract()
logger.info(f"Extracted {len(accounts)} accounts")
```

## Data Quality Notes

- **Synthetic orders** are marked with SYNTH- prefixed idempotency keys for easy filtering
- **Idempotency** is enforced via IDEMPOTENCY_KEY in FACT_TRADES - re-running pipeline won't create duplicates
- **Dimension updates** are applied via UPSERT - existing records are updated if status/name changes
- **Unmatched records** are logged as warnings and excluded from fact table (data quality check)

## Performance

- Typical pipeline runtime: ~2-5 minutes (depends on data volumes)
- Batch processing with configurable batch size (default: 1000 rows)
- Parallel file uploads to Snowflake (4 parallel workers)
- Logging adds minimal overhead

## Future Enhancements

- Incremental loading (delta sync via max timestamp)
- Data quality monitoring and metrics
- Slowly Changing Dimension Type 2 (historical tracking)
- Additional aggregate fact tables (daily position snapshots)
- Real-time streaming integration

## Support

For issues or questions, check logs in `python/logs/` directory.
