#!/usr/bin/env bash
# Loads db/ (tables, views, the services' login roles, seed data) into the new RDS
# database by running the db-init image once as an ECS task, inside the VPC.
# Run once, after the first apply: roles.sql creates roles and fails if they exist.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REGION="${AWS_REGION:-eu-west-1}"

CLUSTER="$(tofu -chdir="$ROOT/infra" output -raw cluster)"
INIT="$(tofu -chdir="$ROOT/infra" output -json db_init)"
field() { jq -r --arg k "$1" '.[$k] | if type == "array" then join(",") else . end' <<<"$INIT"; }

TASK_ARN="$(aws ecs run-task --region "$REGION" --cluster "$CLUSTER" --launch-type FARGATE \
  --task-definition "$(field task_definition)" \
  --network-configuration "awsvpcConfiguration={subnets=[$(field subnets)],securityGroups=[$(field security_group)],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)"
echo "Started $TASK_ARN, waiting for it to finish..."

aws ecs wait tasks-stopped --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK_ARN"

read -r EXIT REASON < <(aws ecs describe-tasks --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK_ARN" \
  --query 'tasks[0].[containers[0].exitCode, stoppedReason]' --output text)

echo "--- last log lines"
aws logs tail "$(field log_group)" --region "$REGION" --since 30m | tail -25

if [ "$EXIT" = "0" ]; then
  echo "Database initialised."
else
  echo "db-init failed: exit code $EXIT ($REASON)" >&2
  exit 1
fi
