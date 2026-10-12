FROM python:3.12-slim

WORKDIR /dashboard

# Apply Debian security fixes released since the base image was published
RUN apt-get update && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py ./

# Drop root: the dashboard only reads from Postgres
RUN useradd --create-home --uid 10001 dashboard
USER dashboard

EXPOSE 8501

CMD ["streamlit", "run", "app.py", "--server.address=0.0.0.0", "--server.port=8501", \
     "--server.headless=true", "--browser.gatherUsageStats=false"]
