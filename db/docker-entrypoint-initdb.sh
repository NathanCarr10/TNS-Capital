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
run_sql_dir "$SQL_ROOT/data"