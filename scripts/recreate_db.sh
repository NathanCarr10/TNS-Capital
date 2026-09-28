#!/bin/bash
set -e

# Load .env file if it exists
if [ -f "$(dirname "$0")/../.env" ]; then
    set -a
    source "$(dirname "$0")/../.env"
    set +a
fi

# Database configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-tns_capital}"
DB_USER="${DB_USER:-tns-capital-db-user}"
DB_PASSWORD="${DB_PASSWORD}"

# Check if password is set
if [ -z "$DB_PASSWORD" ]; then
    echo "Error: DB_PASSWORD environment variable is not set"
    exit 1
fi

echo "Recreating database '$DB_NAME' on $DB_HOST:$DB_PORT..."

# Export password for psql
export PGPASSWORD="$DB_PASSWORD"

# Create/drop tables (recreate schema)
echo "Creating tables..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/tables/01_accounts.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/tables/02_instruments.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/tables/03_orders.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/tables/04_positions.sql"

# Populate with seed data
echo "Populating data..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/data/01_accounts.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/data/02_instruments.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/data/03_orders.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$(dirname "$0")/data/04_positions.sql"

echo "Database recreation completed successfully!"
