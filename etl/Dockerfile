FROM python:3.12-slim

WORKDIR /etl

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py schema.sql ./

# Run the pipeline every day at ETL_SCHEDULE_TIME (UTC inside the container)
CMD ["python", "pipeline.py", "--schedule"]
