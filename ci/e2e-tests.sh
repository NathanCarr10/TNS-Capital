#!/bin/sh
# Runs the Playwright end-to-end suite (frontend/e2e) against a throwaway copy of the whole
# stack: it builds and starts the frontend and everything it depends on under a unique
# Compose project with no host ports (ci/docker-compose.e2e.yml), runs Playwright in the
# pinned Playwright image on that project's network, then removes the stack, its volumes
# and its images. Writes frontend/e2e-results.xml (JUnit), frontend/playwright-report/,
# traces of failed tests in frontend/test-results/, and on failure the services' logs in
# frontend/e2e-stack.log.
#
# Usage: ci/e2e-tests.sh     (run from the repo root; needs Docker and Compose v2.24+)
set -eu

ROOT=${WORKSPACE:-$PWD}
PLAYWRIGHT_IMAGE=${PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.64.0-noble}
PROJECT="e2e-$(date +%s%N)"
ENV_FILE=$(mktemp)

if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"; else COMPOSE="docker-compose"; fi
COMPOSE="$COMPOSE --project-name $PROJECT --project-directory $ROOT --env-file $ENV_FILE \
    -f $ROOT/docker-compose.yml -f $ROOT/ci/docker-compose.e2e.yml"

# Fresh secrets, no market data keys (each order carries its own price), a fast broker, and
# an auth rate limit the journeys can't trip. Replaces the repo's .env for this run.
cat > "$ENV_FILE" <<VARS
DB_USER=tns-capital-db-user
DB_PASSWORD=$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
JWT_SECRET=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
AUTH_RATE_LIMIT=1000
BROKER_MIN_DELAY=100ms
BROKER_MAX_DELAY=500ms
VARS

cleanup() {
    status=$?
    if [ "$status" -ne 0 ]; then
        $COMPOSE logs --no-color --tail 300 > "$ROOT/frontend/e2e-stack.log" 2>&1 || true
    fi
    $COMPOSE down --volumes --rmi local --remove-orphans >/dev/null 2>&1 || true
    rm -f "$ENV_FILE"
    exit "$status"
}
trap cleanup EXIT INT TERM

rm -rf "$ROOT/frontend/playwright-report" "$ROOT/frontend/test-results" \
    "$ROOT/frontend/e2e-results.xml" "$ROOT/frontend/e2e-stack.log"

# The frontend depends on the API, auth service and dashboard, and those on the rest.
# --wait returns once the health checks pass; the suite's global setup then waits for
# the API itself (it has no health check).
$COMPOSE up --detach --build --wait --wait-timeout 300 frontend

# --ipc=host: Chromium needs more shared memory than Docker's 64 MB default
docker run --rm --network "${PROJECT}_default" --ipc=host \
    --user "$(id -u):$(id -g)" -e HOME=/tmp -e CI=1 -e E2E_BASE_URL=http://frontend:8080 \
    -v "$ROOT":/repo -w /repo/frontend "$PLAYWRIGHT_IMAGE" \
    bash -c '[ -d node_modules/@playwright/test ] || npm ci --no-audit --no-fund; npx playwright test'
