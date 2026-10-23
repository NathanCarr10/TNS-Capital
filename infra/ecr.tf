# One repository per image. db-init is the db/ image, run once to load the schema into
# RDS; kafka is a mirror of the public image, since the private subnets have no
# route to Docker Hub.
locals {
  repositories = toset([
    "app", "auth-service", "strategy-service", "broker-simulator",
    "etl", "dashboard", "db-init", "kafka",
  ])
}

resource "aws_ecr_repository" "this" {
  for_each = local.repositories

  name                 = "${var.prefix}-${each.key}"
  image_tag_mutability = "MUTABLE"
  # tofu destroy removes the images too
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "keep_recent" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 5 newest images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}
