terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Remote state in S3, locked with an S3 lock file (Terraform 1.10+; no DynamoDB table needed).
  # Create the bucket once by hand, with versioning on, then: terraform init -backend-config=backend.hcl
  backend "s3" {
    key          = "itl-pay/terraform.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}
