# Trade Database

PostgreSQL 16 schema and seed data for TNS Capital. This folder builds a
Postgres image that loads the SQL below the first time it starts.

## Contents

```
db/
├── Dockerfile                 # Postgres 16 image preloaded with the SQL below
├── docker-entrypoint-init.sh  # runs the SQL subfolders, in order, on first start
├── tables/                    # DDL: accounts, instruments, orders, positions, order_history
├── data/                      # seed data: 10 accounts, 10 instruments, orders, positions
└── scripts/                   # reserved for future use (currently empty)
```

On first start, `docker-entrypoint-init.sh` runs every `.sql` file in each
folder, in file-name order: `tables` → `stored_procs` → `views` → `data` →
`scripts`. Folders that don't exist yet are skipped.

This SQL is the only source of the schema. The API runs Hibernate with
`ddl-auto: validate`, so it checks that the Java entities match these tables
and refuses to start if they don't, but never creates or drops anything. The
`dev` profile loads the same files into an in-memory H2 database.

## Running it

From the project root, with `DB_PASSWORD` set in `.env`:

```bash
# just the database
docker-compose up --build -d postgres

# check what ran on first start
docker-compose logs postgres | grep "Running "

# open psql inside the container
docker-compose exec postgres psql -U tns-capital-db-user -d tns_capital
```

## Changing the schema or seed data

The init scripts only run when the data volume is empty. After editing any
file here, reset the database, which deletes all its data:

```bash
docker-compose down -v
docker-compose up --build -d
```

If you change a table, update the matching entity in
`src/main/java/com/neueda/leap/model/` too, or the API will not start.
