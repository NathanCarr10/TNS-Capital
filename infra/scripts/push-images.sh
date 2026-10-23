#!/usr/bin/env bash
# Builds every service image and pushes it to ECR tagged with the commit SHA, and
# mirrors the Kafka image (the private subnets cannot pull from Docker Hub).
# Usage: infra/scripts/push-images.sh [tag]   (default: the current commit's short SHA)
# Needs the ECR repositories, so run "tofu apply -target=aws_ecr_repository.this" first.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAG="${1:-$(git -C "$ROOT" rev-parse --short HEAD)}"
REGION="${AWS_REGION:-eu-west-1}"
REPOS="$(tofu -chdir="$ROOT/infra" output -json ecr_repositories)"
repo() { jq -r --arg n "$1" '.[$n]' <<<"$REPOS"; }
REGISTRY="$(tofu -chdir="$ROOT/infra" output -raw ecr_registry)"
KAFKA_TAG="7.5.0"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

# image name -> build context, as in docker-compose.yml
declare -A CONTEXT=(
  [app]="$ROOT"
  [auth-service]="$ROOT/shared/auth-service"
  [strategy-service]="$ROOT/strategy-service"
  [broker-simulator]="$ROOT/broker-simulator"
  [etl]="$ROOT/etl"
  [dashboard]="$ROOT/dashboard"
  [db-init]="$ROOT/db"
)

# One at a time: parallel Maven builds run this machine out of memory
for name in app auth-service strategy-service broker-simulator etl dashboard db-init; do
  image="$(repo "$name"):$TAG"
  echo "== $name -> $image"
  docker build -t "$image" "${CONTEXT[$name]}"
  docker push "$image"
done

echo "== kafka (mirror of confluentinc/cp-kafka:$KAFKA_TAG)"
docker pull "confluentinc/cp-kafka:$KAFKA_TAG"
docker tag "confluentinc/cp-kafka:$KAFKA_TAG" "$(repo kafka):$KAFKA_TAG"
docker push "$(repo kafka):$KAFKA_TAG"

echo "Pushed tag $TAG. Deploy it with: tofu apply -var-file=training.tfvars -var image_tag=$TAG"
