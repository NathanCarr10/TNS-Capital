FROM python:3.12-slim

WORKDIR /etl

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY tns_etl ./tns_etl

RUN useradd --system etl
USER etl

# Runs daily at ETL_SCHEDULE_TIME (container time, UTC by default).
CMD ["python", "-m", "tns_etl", "schedule"]
