# Everything docker-compose reads from .env, generated here and held in one Secrets
# Manager secret (one secret, not ten: each costs $0.40 a month). ECS injects single
# keys from it into the containers at startup, so no secret is baked into an image.
# The database master password is separate: RDS generates and stores it itself.

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "random_password" "strategy_client_secret" {
  length  = 64
  special = false
}

resource "random_password" "admin" {
  length = 20
}

resource "random_password" "support" {
  length = 20
}

resource "random_password" "demo" {
  length = 16
}

# The services' least-privilege login roles (db/roles/roles.sql)
resource "random_password" "db_role" {
  for_each = toset(["api", "auth", "etl", "reader", "strategy"])

  length  = 32
  special = false
}

locals {
  alpaca_enabled = var.alpaca_api_key_id != ""

  secret_values = merge(
    {
      JWT_SECRET             = random_password.jwt_secret.result
      STRATEGY_CLIENT_SECRET = random_password.strategy_client_secret.result
      ADMIN_PASSWORD         = random_password.admin.result
      SUPPORT_PASSWORD       = random_password.support.result
      DEMO_PASSWORD          = random_password.demo.result
      DB_API_PASSWORD        = random_password.db_role["api"].result
      DB_AUTH_PASSWORD       = random_password.db_role["auth"].result
      DB_ETL_PASSWORD        = random_password.db_role["etl"].result
      DB_READER_PASSWORD     = random_password.db_role["reader"].result
      DB_STRATEGY_PASSWORD   = random_password.db_role["strategy"].result
    },
    local.alpaca_enabled ? {
      ALPACA_API_KEY_ID     = var.alpaca_api_key_id
      ALPACA_API_SECRET_KEY = var.alpaca_api_secret_key
    } : {}
  )
}

resource "aws_secretsmanager_secret" "app" {
  name        = "${var.prefix}/app"
  description = "TNS Capital service secrets"
  # Delete at once on destroy, so the next apply can reuse the name
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id     = aws_secretsmanager_secret.app.id
  secret_string = jsonencode(local.secret_values)
}
