# One ALB with path rules that copy frontend/nginx.conf: CloudFront sends /api, /auth
# and /analytics here, and each prefix goes to the service nginx sends it to locally.

locals {
  web_services = {
    strategy-service = { port = 8087, health = "/actuator/health", priority = 10, paths = ["/api/v1/strategies", "/api/v1/strategies/*"] }
    app              = { port = 3000, health = "/actuator/health", priority = 20, paths = ["/api/*"] }
    auth-service     = { port = 4000, health = "/health", priority = 30, paths = ["/auth/*"] }
    dashboard        = { port = 8501, health = "/analytics/_stcore/health", priority = 40, paths = ["/analytics", "/analytics/*"] }
  }

  # Target group names are limited to 32 characters
  short_name = { strategy-service = "strategy", app = "app", auth-service = "auth", dashboard = "dash" }
}

resource "aws_lb" "this" {
  name               = "${var.prefix}-alb"
  load_balancer_type = "application"
  internal           = false
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
  # Streamlit's WebSocket and the support chat's streams stay open between messages
  idle_timeout = 300
}

resource "aws_lb_target_group" "web" {
  for_each = local.web_services

  name        = "${var.prefix}-${local.short_name[each.key]}"
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.shared.id
  target_type = "ip"

  # Old tasks drain for 30 s instead of 5 minutes during a deployment
  deregistration_delay = 30

  health_check {
    path                = each.value.health
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "web" {
  for_each = local.web_services

  listener_arn = aws_lb_listener.http.arn
  # Lower runs first: /api/v1/strategies must win over /api/*, as the longer nginx prefix does
  priority = each.value.priority

  condition {
    path_pattern {
      values = each.value.paths
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web[each.key].arn
  }
}
