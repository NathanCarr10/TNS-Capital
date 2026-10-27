terraform {
  # OpenTofu only: the encryption block below is an OpenTofu feature
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Bucket and key come from backend.hcl (tofu init -backend-config=backend.hcl).
  # use_lockfile locks with an S3 object, so no DynamoDB table is needed.
  backend "s3" {
    use_lockfile = true
    encrypt      = true
  }

  # The state holds the generated passwords and JWT secret, and every student in the
  # training account is an administrator who can read the state bucket. Encrypting
  # the state and plan files client-side means the bucket alone gives nothing away.
  encryption {
    key_provider "pbkdf2" "passphrase" {
      passphrase = var.state_passphrase
    }
    method "aes_gcm" "main" {
      keys = key_provider.pbkdf2.passphrase
    }
    state {
      method   = method.aes_gcm.main
      enforced = true
    }
    plan {
      method   = method.aes_gcm.main
      enforced = true
    }
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "tns-capital", Owner = var.prefix, ManagedBy = "OpenTofu" }
  }
}
