#!/bin/bash
# Runs the repo's SQL folders, in dependency order, against the newly
# initialised database. Invoked once by the postgres image on first start.
set -euo pipefail

SQL_ROOT="/docker-entrypoint-initdb.d/sql"

run_sql_dir() {
    local dir="$1"
    [ -d "$dir" ] || return 0

    shopt -s nullglob
    local files=("$dir"/*.sql)
    shopt -u nullglob

    for f in "${files[@]}"; do
        echo "Running $f"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
    done
}

run_sql_dir "$SQL_ROOT/tables"
run_sql_dir "$SQL_ROOT/stored_procs"
run_sql_dir "$SQL_ROOT/views"
run_sql_dir "$SQL_ROOT/data"
run_sql_dir "$SQL_ROOT/scripts"
