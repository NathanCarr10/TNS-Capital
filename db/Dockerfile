FROM postgres:16

# Copy table schemas
COPY tables/ /docker-entrypoint-initdb.d/tables/

# Copy reporting views (the init script runs them after the tables)
COPY views/ /docker-entrypoint-initdb.d/views/

# Copy initial data
COPY data/ /docker-entrypoint-initdb.d/data/

COPY docker-entrypoint-initdb.sh /docker-entrypoint-initdb.d/00-run-sql.sh
RUN chmod -R a+rX /docker-entrypoint-initdb.d \
 && chmod 755 /docker-entrypoint-initdb.d/00-run-sql.sh
