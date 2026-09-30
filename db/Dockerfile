# Trade Database image: PostgreSQL preloaded with the TNS Capital schema
# and seed data.
FROM postgres:16-alpine

# Defaults so the image runs standalone (`docker run -e POSTGRES_PASSWORD=...`)
# without compose. There is no default password on purpose.
# docker-compose.yml overrides these from the project's .env file.
ENV POSTGRES_USER=tns-capital-db-user \
    POSTGRES_DB=tns_capital

# SQL content, copied in as-is so the repo layout and the image layout match.
COPY tables/   /docker-entrypoint-initdb.d/sql/tables/
COPY data/     /docker-entrypoint-initdb.d/sql/data/
COPY scripts/  /docker-entrypoint-initdb.d/sql/scripts/

# Postgres only auto-runs flat files in /docker-entrypoint-initdb.d, so this
# script walks the sql/ subfolders itself, in order, on first start.
COPY docker-entrypoint-init.sh /docker-entrypoint-initdb.d/00-run-sql.sh
RUN chmod -R a+rX /docker-entrypoint-initdb.d \
 && chmod 755 /docker-entrypoint-initdb.d/00-run-sql.sh
