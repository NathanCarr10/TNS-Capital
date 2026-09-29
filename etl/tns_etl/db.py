"""PostgreSQL connection helpers."""

import psycopg2

from tns_etl.config import EtlConfig

# Errors worth retrying: the database was unreachable, restarted, or dropped the
# connection. Anything else (bad SQL, constraint violations) will fail again.
TRANSIENT_DB_ERRORS = (psycopg2.OperationalError, psycopg2.InterfaceError)


def connect(config: EtlConfig):
    return psycopg2.connect(**config.connection_kwargs())
