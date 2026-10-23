# The docker-compose services as ECS Fargate services. Each one registers in a private
# DNS namespace (Cloud Map), so services find each other as <name>.<namespace>, the way
# Compose services find each other by name. Docker-compose.yml is the reference for
# every value here.

locals {
  namespace = "${var.prefix}.internal"
  dns       = { for k in ["kafka", "broker-simulator", "auth-service", "app"] : k => "${k}.${local.namespace}" }

  db_host  = aws_db_instance.this.address
  jdbc_url = "jdbc:postgresql://${aws_db_instance.this.address}:5432/tns_capital"
  kafka    = "${local.dns["kafka"]}:9092"

  image = { for k, r in aws_ecr_repository.this : k => "${r.repository_url}:${var.image_tag}" }

  # Spring Boot's structured logs and heap sizing, as in docker-compose.yml
  java_env = {
    LOGGING_STRUCTURED_FORMAT_CONSOLE = "ecs"
    JAVA_TOOL_OPTIONS                 = "-XX:MaxRAMPercentage=70"
  }

  # public = true: the task runs in a public subnet with a public IP, so it can reach
  # Alpaca's API without a NAT gateway. Inbound is still only what the app security
  # group allows (the ALB and the other tasks).
  services = {
    kafka = {
      image  = "${aws_ecr_repository.this["kafka"].repository_url}:${var.kafka_image_tag}"
      port   = 9092
      cpu    = 512
      memory = 1024
      public = false
      # A single KRaft node: never run two at once, even during a deployment
      single = true
      env = {
        KAFKA_NODE_ID                          = "1"
        KAFKA_PROCESS_ROLES                    = "broker,controller"
        KAFKA_CONTROLLER_QUORUM_VOTERS         = "1@localhost:9093"
        KAFKA_LISTENERS                        = "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093"
        KAFKA_ADVERTISED_LISTENERS             = "PLAINTEXT://${local.kafka}"
        KAFKA_LISTENER_SECURITY_PROTOCOL_MAP   = "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
        KAFKA_CONTROLLER_LISTENER_NAMES        = "CONTROLLER"
        KAFKA_INTER_BROKER_LISTENER_NAME       = "PLAINTEXT"
        CLUSTER_ID                             = "bXr8gAgKTXCZkbGvyQu4gw"
        KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR = "1"
        KAFKA_AUTO_CREATE_TOPICS_ENABLE        = "false"
        KAFKA_HEAP_OPTS                        = "-Xms512m -Xmx512m"
      }
      secrets = []
    }

    broker-simulator = {
      image  = local.image["broker-simulator"]
      port   = 8085
      cpu    = 512
      memory = 1024
      public = false
      single = false
      env = merge(local.java_env, {
        SPRING_KAFKA_BOOTSTRAP_SERVERS = local.kafka
        BROKER_MIN_DELAY               = "500ms"
        BROKER_MAX_DELAY               = "2000ms"
      })
      secrets = []
    }

    auth-service = {
      image  = local.image["auth-service"]
      port   = 4000
      cpu    = 256
      memory = 512
      public = false
      single = false
      env = {
        ADMIN_EMAIL     = var.admin_email
        SUPPORT_EMAIL   = var.support_email
        DB_HOST         = local.db_host
        DB_PORT         = "5432"
        DB_NAME         = "tns_capital"
        DB_USER         = "tns_auth"
        PORT            = "4000"
        METRICS_PORT    = "9464"
        LOG_FORMAT      = "json"
        AUTH_RATE_LIMIT = "10"
        # Browser -> CloudFront -> ALB -> task: two proxies append to X-Forwarded-For,
        # so the client's own address is two hops back
        AUTH_TRUST_PROXY = "2"
        # RDS refuses unencrypted connections; node-postgres reads this when the Pool
        # sets no ssl option. no-verify: encrypted, without bundling the RDS CA
        PGSSLMODE = "no-verify"
      }
      secrets = ["JWT_SECRET", "ADMIN_PASSWORD", "SUPPORT_PASSWORD", "DEMO_PASSWORD", "STRATEGY_CLIENT_SECRET", "DB_PASSWORD=DB_AUTH_PASSWORD"]
    }

    app = {
      image  = local.image["app"]
      port   = 3000
      cpu    = 512
      memory = 1024
      public = local.alpaca_enabled
      single = false
      env = merge(local.java_env, {
        SPRING_DATASOURCE_URL          = local.jdbc_url
        SPRING_DATASOURCE_USERNAME     = "tns_api"
        SPRING_KAFKA_BOOTSTRAP_SERVERS = local.kafka
        BROKER_URL                     = "http://${local.dns["broker-simulator"]}:8085"
        METRICS_PORT                   = "9464"
        ALPACA_FEED                    = "iex"
        NEWSLETTER_CRON                = "0 0 17 * * FRI"
        NEWSLETTER_EARLY_PUBLISH       = tostring(var.newsletter_early_publish)
      })
      secrets = concat(
        ["JWT_SECRET", "SPRING_DATASOURCE_PASSWORD=DB_API_PASSWORD"],
        local.alpaca_enabled ? ["ALPACA_API_KEY_ID", "ALPACA_API_SECRET_KEY"] : []
      )
    }

    strategy-service = {
      image  = local.image["strategy-service"]
      port   = 8087
      cpu    = 512
      memory = 1024
      public = false
      single = false
      env = merge(local.java_env, {
        SPRING_DATASOURCE_URL           = local.jdbc_url
        SPRING_DATASOURCE_USERNAME      = "tns_strategy"
        SPRING_KAFKA_BOOTSTRAP_SERVERS  = local.kafka
        TRADING_API_URL                 = "http://${local.dns["app"]}:3000"
        AUTH_URL                        = "http://${local.dns["auth-service"]}:4000"
        STRATEGY_MAX_ACTIVE_PER_ACCOUNT = "3"
      })
      secrets = ["JWT_SECRET", "STRATEGY_CLIENT_SECRET", "SPRING_DATASOURCE_PASSWORD=DB_STRATEGY_PASSWORD"]
    }

    etl = {
      image  = local.image["etl"]
      port   = null
      cpu    = 256
      memory = 512
      public = local.alpaca_enabled
      single = true
      env = {
        ETL_DB_HOST          = local.db_host
        DB_USER              = "tns_etl"
        ETL_SCHEDULE_TIME    = "02:00"
        PRICE_HISTORY_FEED   = "sip"
        PRICE_BACKFILL_YEARS = "5"
      }
      secrets = concat(
        ["DB_PASSWORD=DB_ETL_PASSWORD"],
        local.alpaca_enabled ? ["ALPACA_API_KEY_ID", "ALPACA_API_SECRET_KEY"] : []
      )
    }

    # The Monitoring view's VictoriaMetrics stack is not deployed; the view says so
    dashboard = {
      image  = local.image["dashboard"]
      port   = 8501
      cpu    = 256
      memory = 512
      public = false
      single = false
      env = {
        DASHBOARD_DB_HOST  = local.db_host
        DB_USER            = "tns_reader"
        DASHBOARD_AUTH_URL = "http://${local.dns["auth-service"]}:4000"
      }
      secrets = ["JWT_SECRET", "DB_PASSWORD=DB_READER_PASSWORD"]
    }
  }

  db_init_script = join(" && ", [
    "sed -i '/^CREATE ROLE tns_strategy /a GRANT tns_api, tns_auth, tns_etl, tns_reader, tns_strategy TO CURRENT_USER WITH SET TRUE;' /docker-entrypoint-initdb.d/roles/roles.sql",
    "grep -q 'TO CURRENT_USER WITH SET TRUE' /docker-entrypoint-initdb.d/roles/roles.sql",
    "exec bash /docker-entrypoint-initdb.d/00-run-sql.sh",
  ])

  # "NAME" injects the secret key NAME; "NAME=KEY" injects key KEY as NAME
  secret_refs = {
    for k, s in local.services : k => [
      for ref in s.secrets : {
        name      = split("=", ref)[0]
        valueFrom = "${aws_secretsmanager_secret.app.arn}:${element(split("=", ref), length(split("=", ref)) - 1)}::"
      }
    ]
  }
}

resource "aws_ecs_cluster" "this" {
  name = "${var.prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled" # the free ECS metrics are enough here; Insights is billed
  }
}

resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = local.namespace
  description = "${var.prefix} service-to-service names"
  vpc         = data.aws_vpc.shared.id
}

resource "aws_service_discovery_service" "this" {
  for_each = local.services

  name = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = toset(concat(keys(local.services), ["db-init"]))

  name              = "/ecs/${var.prefix}/${each.key}"
  retention_in_days = 7
}

# --- IAM: the execution role pulls images, writes logs and reads the secrets ---

data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.prefix}-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "read_secrets" {
  name = "${var.prefix}-read-secrets"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = [aws_secretsmanager_secret.app.arn, aws_db_instance.this.master_user_secret[0].secret_arn]
    }]
  })
}

# --- Task definitions and services ---

resource "aws_ecs_task_definition" "service" {
  for_each = local.services

  family                   = "${var.prefix}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name         = each.key
    image        = each.value.image
    essential    = true
    portMappings = each.value.port == null ? [] : [{ containerPort = each.value.port, protocol = "tcp" }]
    environment  = [for name, value in each.value.env : { name = name, value = value }]
    secrets      = local.secret_refs[each.key]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "this" {
  for_each = local.services

  name            = each.key
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # Kafka and the ETL scheduler stop the old task before starting the new one
  deployment_minimum_healthy_percent = each.value.single ? 0 : 100
  deployment_maximum_percent         = each.value.single ? 100 : 200

  # Spring Boot takes a minute or so on half a vCPU; don't let the ALB judge it sooner
  health_check_grace_period_seconds = contains(keys(local.web_services), each.key) ? 180 : null

  # No circuit breaker: like Compose without depends_on, a service whose dependencies
  # (Kafka, the database roles) aren't ready yet restarts until they are, and the
  # breaker would count that as a failed deployment and stop launching tasks.

  network_configuration {
    subnets          = each.value.public ? var.public_subnet_ids : var.private_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = each.value.public
  }

  service_registries {
    registry_arn = aws_service_discovery_service.this[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = contains(keys(local.web_services), each.key) ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.web[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  depends_on = [aws_lb_listener_rule.web, aws_iam_role_policy.read_secrets]
}

# --- One-off: load db/ (tables, views, roles, seed data) into the new RDS database ---

# RDS does not run docker-entrypoint-initdb.d, so the db/ image runs its own init script
# against RDS instead. scripts/init-db.sh starts it once, after the first apply.
resource "aws_ecs_task_definition" "db_init" {
  family                   = "${var.prefix}-db-init"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = "db-init"
    image     = local.image["db-init"]
    essential = true
    # The postgres image's entrypoint runs any command that isn't "postgres" as given.
    # scripts/init-db.sh --reset runs its own SQL first, then the same INIT_SCRIPT.
    command = ["bash", "-c", "eval \"$INIT_SCRIPT\""]
    environment = [
      { name = "PGHOST", value = local.db_host },
      { name = "PGPORT", value = "5432" },
      { name = "PGSSLMODE", value = "require" },
      # The RDS master user is not a superuser and, unlike plain Postgres 16, gets no
      # membership in the roles it creates, so roles.sql's CREATE SCHEMA ... AUTHORIZATION
      # tns_etl fails. This container's copy of roles.sql (not db/ in the repo) grants it
      # SET on them straight after they are created.
      { name = "INIT_SCRIPT", value = local.db_init_script },
      { name = "DB_USER", value = aws_db_instance.this.username },
    ]
    secrets = [
      { name = "PGPASSWORD", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:password::" },
      { name = "DB_API_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_API_PASSWORD::" },
      { name = "DB_AUTH_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_AUTH_PASSWORD::" },
      { name = "DB_ETL_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_ETL_PASSWORD::" },
      { name = "DB_READER_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_READER_PASSWORD::" },
      { name = "DB_STRATEGY_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_STRATEGY_PASSWORD::" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service["db-init"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "db-init"
      }
    }
  }])
}
