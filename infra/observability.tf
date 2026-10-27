# CloudWatch stands in for the local VictoriaMetrics/VictoriaLogs stack: the services
# log to CloudWatch Logs (ecs.tf), and ECS reports CPU and memory per service for free.

resource "aws_cloudwatch_metric_alarm" "app_cpu" {
  alarm_name          = "${var.prefix}-app-high-cpu"
  alarm_description   = "Trading API above 80% CPU for 10 minutes"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.this.name, ServiceName = aws_ecs_service.this["app"].name }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  for_each = local.web_services

  alarm_name          = "${var.prefix}-${local.short_name[each.key]}-unhealthy"
  alarm_description   = "${each.key} has no healthy task behind the ALB"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = aws_lb.this.arn_suffix, TargetGroup = aws_lb_target_group.web[each.key].arn_suffix }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.prefix}-tns-capital"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title   = "CPU by service (%)"
          region  = var.region
          stat    = "Average"
          period  = 60
          metrics = [for k in keys(local.services) : ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", k]]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
        properties = {
          title   = "Memory by service (%)"
          region  = var.region
          stat    = "Average"
          period  = 60
          metrics = [for k in keys(local.services) : ["AWS/ECS", "MemoryUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", k]]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6
        properties = {
          title  = "ALB requests and 5xx"
          region = var.region
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.this.arn_suffix],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.this.arn_suffix],
          ]
        }
      },
      {
        type = "log", x = 12, y = 6, width = 12, height = 6
        properties = {
          title  = "Recent errors"
          region = var.region
          query  = "SOURCE '/ecs/${var.prefix}/app' | SOURCE '/ecs/${var.prefix}/auth-service' | SOURCE '/ecs/${var.prefix}/strategy-service' | fields @timestamp, @logStream, @message | filter @message like /(?i)error/ | sort @timestamp desc | limit 20"
          view   = "table"
        }
      },
    ]
  })
}

resource "aws_budgets_budget" "monthly" {
  count = var.budget_email == "" ? 0 : 1

  name         = "${var.prefix}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_filter {
    name = "TagKeyValue"
    # Only counts once Owner is activated as a cost allocation tag (Billing console)
    values = [format("user:Owner$%s", var.prefix)]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }
}
