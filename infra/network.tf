# Security groups only. The VPC, subnets, route tables and the VPC endpoints for ECR,
# CloudWatch Logs, Secrets Manager and S3 are shared by the class and not managed here.

data "aws_vpc" "shared" {
  id = var.vpc_id
}

# CloudFront's origin-facing addresses: the ALB accepts traffic from CloudFront only,
# so nobody can skip the CDN and its security headers by calling the ALB directly
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.prefix}-alb-sg"
  description = "${var.prefix} ALB, HTTP from CloudFront only"
  vpc_id      = data.aws_vpc.shared.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront edge locations"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_egress_rule" "alb_to_vpc" {
  security_group_id = aws_security_group.alb.id
  description       = "To the tasks"
  ip_protocol       = "tcp"
  from_port         = 0
  to_port           = 65535
  cidr_ipv4         = data.aws_vpc.shared.cidr_block
}

# Every ECS task. Tasks reach each other (Kafka, the broker simulator, auth) through
# the self rule, and the ALB only on the ports it routes to.
resource "aws_security_group" "app" {
  name        = "${var.prefix}-app-sg"
  description = "${var.prefix} ECS tasks"
  vpc_id      = data.aws_vpc.shared.id
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  for_each = local.web_services

  security_group_id            = aws_security_group.app.id
  description                  = "${each.key} from the ALB"
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_ingress_rule" "app_self" {
  security_group_id            = aws_security_group.app.id
  description                  = "Service to service"
  ip_protocol                  = "tcp"
  from_port                    = 0
  to_port                      = 65535
  referenced_security_group_id = aws_security_group.app.id
}

# Out to the VPC endpoints (443), RDS (5432) and, for the tasks in public subnets, Alpaca
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "All outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_security_group" "rds" {
  name        = "${var.prefix}-rds-sg"
  description = "${var.prefix} RDS, 5432 from the ECS tasks only"
  vpc_id      = data.aws_vpc.shared.id
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  security_group_id            = aws_security_group.rds.id
  description                  = "Postgres from the ECS tasks"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.app.id
}
