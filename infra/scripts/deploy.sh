#!/usr/bin/env bash
# Deploys one commit: the images to ECR, new task definitions through OpenTofu, the
# frontend to S3, then checks the site answers through CloudFront. Jenkins runs it for
# main; it works the same by hand.
#
# Usage: infra/scripts/deploy.sh [tag]   (default: the current commit's short SHA)
# Needs TF_VAR_state_passphrase (and TF_VAR_alpaca_* if Alpaca is used) in the environment.
#
# A release only changes task definitions and services. If the plan changes anything else
# (a security group, the ALB, the database, the secret) it stops before applying, so an
# infrastructure change is never applied by a pipeline nobody is watching. Read the plan,
# then apply it yourself, or rerun with ALLOW_INFRA_CHANGES=1.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAG="${1:-$(git -C "$ROOT" rev-parse --short HEAD)}"
REGION="${AWS_REGION:-eu-west-1}"
: "${TF_VAR_state_passphrase:?set TF_VAR_state_passphrase (source ~/.tns-tofu.env)}"

cd "$ROOT/infra"
VARS=(-var-file=training.tfvars -var "image_tag=$TAG")

echo "== Setup"
# A fresh checkout (every Jenkins build) has no backend.hcl: the bucket name follows from
# the prefix and the account, as bootstrap/ names it
if [ ! -d .terraform ]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
  PREFIX="$(sed -n 's/^prefix *= *"\(.*\)"/\1/p' training.tfvars)"
  tofu init -input=false \
    -backend-config="bucket=${PREFIX}-tfstate-${ACCOUNT}" \
    -backend-config="key=tns-capital/terraform.tfstate" \
    -backend-config="region=${REGION}"
fi

echo "== Build and push ($TAG)"
scripts/push-images.sh "$TAG"

echo "== Plan"
tofu plan -input=false -lock-timeout=5m "${VARS[@]}" -out=tfplan
CHANGES="$(tofu show -json tfplan | jq -r '.resource_changes[]
  | select(.mode == "managed" and .change.actions != ["no-op"])
  | "\(.type) \(.address) \(.change.actions | join("/"))"')"
echo "${CHANGES:-no changes}"

OTHER="$(grep -vE '^(aws_ecs_task_definition|aws_ecs_service) ' <<<"$CHANGES" || true)"
if [ -n "$OTHER" ] && [ "${ALLOW_INFRA_CHANGES:-0}" != "1" ]; then
  echo "The plan changes infrastructure, not just the release:" >&2
  echo "$OTHER" >&2
  echo "Review it and apply by hand (tofu apply -var-file=training.tfvars -var image_tag=$TAG)," >&2
  echo "or rerun with ALLOW_INFRA_CHANGES=1." >&2
  rm -f tfplan
  exit 1
fi

echo "== Deploy"
tofu apply -input=false -lock-timeout=5m tfplan
rm -f tfplan

# Rolling deployments: each old task serves until its replacement passes the health check
CLUSTER="$(tofu output -raw cluster)"
aws ecs wait services-stable --region "$REGION" --cluster "$CLUSTER" \
  --services kafka broker-simulator auth-service app strategy-service etl dashboard

scripts/publish-frontend.sh

echo "== Verify"
URL="$(tofu output -raw url)"
check() {
  local path="$1" want="$2" got
  got="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$URL$path")"
  printf '%-30s %s (want %s)\n' "$path" "$got" "$want"
  [ "$got" = "$want" ]
}
FAILED=0
check /                          200 || FAILED=1  # the Angular app, from S3
check /api/v1/instruments        401 || FAILED=1  # reached the trading API, which wants a token
check /analytics/_stcore/health  200 || FAILED=1  # the dashboard
[ "$FAILED" = "0" ] || { echo "== VERIFY FAILED" >&2; exit 1; }

# The pushed images are in ECR now; don't keep a local copy of every release
REGISTRY="$(tofu output -raw ecr_registry)"
docker images --format '{{.Repository}}:{{.Tag}}' | grep "^$REGISTRY/.*:$TAG$" | xargs -r docker rmi > /dev/null || true

echo "== DEPLOYED $TAG to $URL"
