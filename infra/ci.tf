# What Jenkins deploys with (Sprint 11 Module 11): a role for the Jenkins host's EC2
# instance profile, so no AWS keys are stored in Jenkins. The instance profile hands the
# instance short-lived credentials that rotate on their own, and stop working the moment
# the profile is detached.
#
# The role can read everything (tofu plan refreshes every resource in the state) but can
# only change what a release changes: push images, register task definitions, update the
# services, upload the frontend and write the state. Anything else in a plan (an ALB, a
# security group, the database) fails with AccessDenied, and scripts/deploy.sh stops
# before even trying: infrastructure changes are applied by a person who has read the plan.

data "aws_iam_policy_document" "ec2_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "jenkins_deploy" {
  name               = "${var.prefix}-jenkins-deploy"
  assume_role_policy = data.aws_iam_policy_document.ec2_trust.json
}

resource "aws_iam_instance_profile" "jenkins_deploy" {
  name = "${var.prefix}-jenkins-deploy"
  role = aws_iam_role.jenkins_deploy.name
}

resource "aws_iam_role_policy_attachment" "jenkins_read" {
  role       = aws_iam_role.jenkins_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

locals {
  state_bucket = "${var.prefix}-tfstate-${data.aws_caller_identity.current.account_id}"
}

data "aws_iam_policy_document" "jenkins_deploy" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage",
    ]
    resources = [for r in aws_ecr_repository.this : r.arn]
  }

  # Task definitions have no resource-level permissions for these actions
  statement {
    sid       = "TaskDefinitions"
    actions   = ["ecs:RegisterTaskDefinition", "ecs:DeregisterTaskDefinition", "ecs:TagResource"]
    resources = ["*"]
  }

  statement {
    sid       = "UpdateServices"
    actions   = ["ecs:UpdateService"]
    resources = ["arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/*"]
  }

  # Only the execution role, and only to ECS tasks
  statement {
    sid       = "PassExecutionRoleOnly"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.execution.arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # tofu refreshes the secret's value; ReadOnlyAccess does not include it
  statement {
    sid       = "RefreshAppSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.app.arn]
  }

  statement {
    sid       = "StateAndLock"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${local.state_bucket}/tns-capital/*"]
  }

  statement {
    sid       = "FrontendUpload"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid       = "ListBuckets"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.state_bucket}", aws_s3_bucket.frontend.arn]
  }

  statement {
    sid       = "InvalidateCdn"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.this.arn]
  }
}

resource "aws_iam_role_policy" "jenkins_deploy" {
  name   = "${var.prefix}-jenkins-deploy"
  role   = aws_iam_role.jenkins_deploy.id
  policy = data.aws_iam_policy_document.jenkins_deploy.json
}

output "jenkins_instance_profile" {
  description = "Attach to the Jenkins host's EC2 instance: aws ec2 associate-iam-instance-profile"
  value       = aws_iam_instance_profile.jenkins_deploy.name
}
