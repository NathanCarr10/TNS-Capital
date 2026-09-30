FROM postgres:16

# Copy table schemas
COPY tables/ /docker-entrypoint-initdb.d/tables/

# Copy initial data
COPY data/ /docker-entrypoint-initdb.d/data/
