output "url" {
  description = "The platform, served by CloudFront"
  value       = "https://${aws_cloudfront_distribution.this.domain_name}"
}

output "distribution_id" {
  value = aws_cloudfront_distribution.this.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.id
}

output "alb_dns" {
  description = "Accepts CloudFront's addresses only; use the url output in a browser"
  value       = aws_lb.this.dns_name
}

output "cluster" {
  value = aws_ecs_cluster.this.name
}

output "ecr_registry" {
  value = split("/", aws_ecr_repository.this["app"].repository_url)[0]
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "rds_endpoint" {
  value = aws_db_instance.this.address
}

# What scripts/init-db.sh needs to start the one-off task
output "db_init" {
  value = {
    task_definition = aws_ecs_task_definition.db_init.arn
    subnets         = var.private_subnet_ids
    security_group  = aws_security_group.app.id
    log_group       = aws_cloudwatch_log_group.service["db-init"].name
  }
}

output "secret_arn" {
  description = "Read a value with: aws secretsmanager get-secret-value --secret-id <arn> --query SecretString --output text | jq -r .ADMIN_PASSWORD"
  value       = aws_secretsmanager_secret.app.arn
}

output "logins" {
  description = "Who can sign in; the passwords are in the secret above"
  value = {
    admin   = "${var.admin_email} (ADMIN_PASSWORD)"
    support = "${var.support_email} (SUPPORT_PASSWORD)"
    demo    = "jane.doe@example.com (DEMO_PASSWORD)"
  }
}
