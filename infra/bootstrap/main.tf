# One-off: the S3 bucket that holds the main stack's state. Applied once with local
# state (this stack holds no secrets), and never destroyed with the rest: tearing down
# the platform must not delete the record of what was created.

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "prefix" {
  description = "Starts every resource name, e.g. tns-capital"
  type        = string
}

variable "region" {
  type    = string
  default = "eu-west-1"
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "tns-capital", Owner = var.prefix, ManagedBy = "OpenTofu" }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  # Bucket names are global: the account ID keeps it unique
  bucket = "${var.prefix}-tfstate-${data.aws_caller_identity.current.account_id}"
}

# Old state versions let a bad apply be rolled back by hand
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

output "state_bucket" {
  value = aws_s3_bucket.state.id
}
