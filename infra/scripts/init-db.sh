#!/usr/bin/env bash
# Loads db/ (tables, views, the services' login roles, seed data) into the new RDS
# database by running the db-init image once as an ECS task, inside the VPC.
# Run once, after the first apply: roles.sql creates roles and fails if they exist.
# --reset first drops the database and the service roles (all data is lost), for a
# database a failed run left half-initialised.
set -euo pipefail

RESET=false
[ "${1:-}" = "--reset" ] && RESET=true

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REGION="${AWS_REGION:-eu-west-1}"

CLUSTER="$(tofu -chdir="$ROOT/infra" output -raw cluster)"
INIT="$(tofu -chdir="$ROOT/infra" output -json db_init)"
field() { jq -r --arg k "$1" '.[$k] | if type == "array" then join(",") else . end' <<<"$INIT"; }

OVERRIDES='{}'
if $RESET; then
  SQL='DROP DATABASE IF EXISTS tns_capital WITH (FORCE);'
  for r in tns_api tns_auth tns_etl tns_reader tns_strategy; do SQL+=" DROP ROLE IF EXISTS $r;"; done
  SQL+=' CREATE DATABASE tns_capital;'
  OVERRIDES="$(jq -n --arg sql "$SQL" '{containerOverrides: [{name: "db-init", command: ["bash", "-c",
    "set -e; IFS=\";\"; for s in $SQL; do [ -n \"${s// }\" ] && psql -v ON_ERROR_STOP=1 -U \"$DB_USER\" -d postgres -c \"$s\"; done; eval \"$INIT_SCRIPT\""],
    environment: [{name: "SQL", value: $sql}]}]}')"
  echo "Resetting the database first: all its data will be dropped."
fi

TASK_ARN="$(aws ecs run-task --region "$REGION" --cluster "$CLUSTER" --launch-type FARGATE \
  --task-definition "$(field task_definition)" \
  --network-configuration "awsvpcConfiguration={subnets=[$(field subnets)],securityGroups=[$(field security_group)],assignPublicIp=DISABLED}" \
  --overrides "$OVERRIDES" \
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
