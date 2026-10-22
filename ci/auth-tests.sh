#!/bin/sh
# Runs the auth service's Jest suite in node:22-alpine (the image its container uses), with its
# Postgres tests switched on against a throwaway database built from db/ (the real schema),
# which is removed afterwards. Writes shared/auth-service/coverage/lcov.info for SonarQube.
#
# Usage: ci/auth-tests.sh     (run from the repo root)
set -eu

ROOT=${WORKSPACE:-$PWD}
ID="authit-$(date +%s%N)"
PASSWORD=$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
trap 'docker rm -f "$ID-pg" >/dev/null 2>&1 || true; docker network rm "$ID" >/dev/null 2>&1 || true;
      docker rmi "$ID-db" >/dev/null 2>&1 || true' EXIT

docker build -q -t "$ID-db" "$ROOT/db" >/dev/null
docker network create "$ID" >/dev/null
docker run -d --name "$ID-pg" --network "$ID" -e POSTGRES_DB=tns_capital -e POSTGRES_USER=tns \
    -e POSTGRES_PASSWORD="$PASSWORD" -e DB_USER=tns "$ID-db" >/dev/null
# -h localhost checks over TCP, which only opens once the schema scripts have run
tries=0
until docker exec "$ID-pg" pg_isready -h localhost -U tns -d tns_capital >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge 90 ]; then echo "Postgres did not start" >&2; exit 1; fi
    sleep 1
done

docker run --rm --network "$ID" --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -e DB_HOST="$ID-pg" -e DB_PORT=5432 -e DB_NAME=tns_capital -e DB_USER=tns -e DB_PASSWORD="$PASSWORD" \
    -v "$ROOT":/repo -w /repo/shared/auth-service node:22-alpine \
    sh -c "npm ci --no-audit --no-fund && npm test -- --coverage"
