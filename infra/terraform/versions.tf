terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.60, < 7.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State holds the Google secrets and generated passwords. Local state is fine for one person; for a team, move it to an
  # S3 bucket with versioning + encryption:
  #   backend "s3" { bucket = "..." key = "gradientnova/terraform.tfstate" region = "ap-south-1" }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.name_prefix
      ManagedBy = "terraform"
    }
  }
}

# CloudFront's WAF web ACLs can only be created in us-east-1, whatever region the app runs in.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project   = var.name_prefix
      ManagedBy = "terraform"
    }
  }
}
