#!/usr/bin/env bash
# Builds the Angular app with the frontend Dockerfile's build stage (it generates the
# API clients from contracts/, which needs a JRE), uploads it to the S3 bucket and
# clears CloudFront's cache.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BUCKET="$(tofu -chdir="$ROOT/infra" output -raw frontend_bucket)"
DIST_ID="$(tofu -chdir="$ROOT/infra" output -raw distribution_id)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"; docker rm -f tns-frontend-build >/dev/null 2>&1 || true' EXIT

docker build --target build --build-context contracts="$ROOT/contracts" \
  -t tns-frontend-build "$ROOT/frontend"
docker create --name tns-frontend-build tns-frontend-build >/dev/null
docker cp tns-frontend-build:/repo/frontend/dist/frontend/browser/. "$OUT"

# Hashed bundles never change (as nginx's 1y immutable); everything else is revalidated
aws s3 sync "$OUT" "s3://$BUCKET" --delete --exclude '*' --include '*.js' --include '*.css' \
  --cache-control 'public, max-age=31536000, immutable'
aws s3 sync "$OUT" "s3://$BUCKET" --delete --exclude '*.js' --exclude '*.css' \
  --cache-control 'no-cache'

aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths '/*' \
  --query 'Invalidation.Status' --output text
echo "Published to https://$(tofu -chdir="$ROOT/infra" output -raw url | sed 's#https://##')"
