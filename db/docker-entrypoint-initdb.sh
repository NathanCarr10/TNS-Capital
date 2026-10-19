set -euo pipefail

SQL_ROOT="/docker-entrypoint-initdb.d"

run_sql_dir() {
    local dir="$1"
    [ -d "$dir" ] || return 0

    shopt -s nullglob
    local files=("$dir"/*.sql)
    shopt -u nullglob

    for f in "${files[@]}"; do
        echo "Running $f"
        psql -v ON_ERROR_STOP=1 --username "$DB_USER" --dbname tns_capital -f "$f"
    done
}

run_sql_dir "$SQL_ROOT/tables"
# run_sql_dir "$SQL_ROOT/stored_procs"
run_sql_dir "$SQL_ROOT/views"

# One least-privilege login role per service (roles/roles.sql). Each password
# defaults to the owner's, so a .env with only DB_PASSWORD still works
if [ -f "$SQL_ROOT/roles/roles.sql" ]; then
    echo "Running $SQL_ROOT/roles/roles.sql"
    psql -v ON_ERROR_STOP=1 --username "$DB_USER" --dbname tns_capital \
        -v api_password="${DB_API_PASSWORD:-$POSTGRES_PASSWORD}" \
        -v auth_password="${DB_AUTH_PASSWORD:-$POSTGRES_PASSWORD}" \
        -v etl_password="${DB_ETL_PASSWORD:-$POSTGRES_PASSWORD}" \
        -v reader_password="${DB_READER_PASSWORD:-$POSTGRES_PASSWORD}" \
        -v strategy_password="${DB_STRATEGY_PASSWORD:-$POSTGRES_PASSWORD}" \
        -f "$SQL_ROOT/roles/roles.sql"
fi

run_sql_dir "$SQL_ROOT/data"
