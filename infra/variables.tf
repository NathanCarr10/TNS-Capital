variable "prefix" {
  description = "Starts every resource name (the training account's rule). Lowercase, 16 characters or fewer"
  type        = string
  validation {
    condition     = can(regex("^[a-z0-9-]{1,16}$", var.prefix))
    error_message = "prefix must be lowercase letters, digits and hyphens, at most 16 characters."
  }
}

variable "region" {
  type    = string
  default = "eu-west-1"
}

variable "state_passphrase" {
  description = "Encrypts the state file (at least 16 characters). Set TF_VAR_state_passphrase; losing it makes the state unreadable"
  type        = string
  sensitive   = true
}

# The training account's shared default VPC. Its subnets and VPC endpoints belong to
# the instructor: they are read here, never created or destroyed by this stack.
variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  description = "Two subnets in different AZs with a route to the internet gateway (ALB, and tasks that call Alpaca)"
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Two subnets in different AZs with no internet route (the other tasks and RDS)"
  type        = list(string)
}

variable "image_tag" {
  description = "Tag of the service images in ECR, normally the git commit SHA"
  type        = string
}

variable "cpu_architecture" {
  description = "X86_64 or ARM64: must match the machine the images were built on"
  type        = string
  default     = "X86_64"
}

variable "kafka_image_tag" {
  description = "confluentinc/cp-kafka tag mirrored into ECR (the private subnets cannot reach Docker Hub)"
  type        = string
  default     = "7.5.0"
}

# Optional: without these the platform runs, but market data from Alpaca does not
variable "alpaca_api_key_id" {
  type      = string
  default   = ""
  sensitive = true
}

variable "alpaca_api_secret_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "admin_email" {
  type    = string
  default = "admin@tns-capital.local"
}

variable "support_email" {
  type    = string
  default = "support@tns-capital.local"
}

variable "newsletter_early_publish" {
  description = "Lets support staff publish the week's newsletter before Friday (handy for the showcase)"
  type        = bool
  default     = true
}

variable "budget_email" {
  description = "Gets the monthly budget alert; leave empty to skip the budget"
  type        = string
  default     = ""
}

variable "monthly_budget_usd" {
  type    = number
  default = 20
}
