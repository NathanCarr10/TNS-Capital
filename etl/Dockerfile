FROM python:3.12-slim

WORKDIR /etl

# Apply Debian security fixes released since the base image was published
# (e.g. OpenSSL), so the image doesn't ship already-fixed CVEs.
RUN apt-get update && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py schema.sql ./

# Drop root: the pipeline only reads config and talks to Postgres, so it needs no
# write access to the image.
RUN useradd --create-home --uid 10001 etl
USER etl

# Run the pipeline every day at ETL_SCHEDULE_TIME (UTC inside the container)
CMD ["python", "pipeline.py", "--schedule"]
