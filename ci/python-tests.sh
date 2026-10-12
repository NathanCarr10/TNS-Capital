#!/bin/sh
# Runs one Python suite (etl or dashboard) in python:3.12-slim, with its
# Postgres integration tests switched on against a throwaway database that is
# removed afterwards. Writes <suite>/test-results.xml (JUnit) and
# <suite>/coverage.xml (paths from the repo root, for SonarQube).
#
# Usage: ci/python-tests.sh etl|dashboard     (run from the repo root)
set -eu

SUITE=${1:-}
case "$SUITE" in
    etl) PREFIX=ETL; REQUIREMENTS="-r etl/requirements-dev.txt" ;;
    dashboard) PREFIX=DASHBOARD; REQUIREMENTS="-r dashboard/requirements.txt -r dashboard/requirements-dev.txt" ;;
    *) echo "usage: $0 etl|dashboard" >&2; exit 2 ;;
esac

ID="pyit-$SUITE-$(date +%s%N)"
PASSWORD=$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
trap 'docker rm -f "$ID-pg" >/dev/null 2>&1 || true; docker network rm "$ID" >/dev/null 2>&1 || true' EXIT

docker network create "$ID" >/dev/null
docker run -d --name "$ID-pg" --network "$ID" -e POSTGRES_PASSWORD="$PASSWORD" postgres:16 >/dev/null
# -h localhost checks over TCP, which only opens once initialisation has finished
tries=0
until docker exec "$ID-pg" pg_isready -h localhost -U postgres >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge 60 ]; then echo "Postgres did not start" >&2; exit 1; fi
    sleep 1
done

docker run --rm --network "$ID" --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -e "${PREFIX}_IT=1" -e "${PREFIX}_DB_HOST=$ID-pg" -e "${PREFIX}_DB_NAME=postgres" \
    -e DB_USER=postgres -e "DB_PASSWORD=$PASSWORD" -e "PYTHONPATH=$SUITE" \
    -v "${WORKSPACE:-$PWD}":/repo -w /repo python:3.12-slim \
    sh -c "pip install --quiet --user $REQUIREMENTS && \
           python -m pytest -p no:cacheprovider $SUITE/tests --junitxml=$SUITE/test-results.xml \
               --cov=$SUITE --cov-report=xml:$SUITE/coverage.xml"
