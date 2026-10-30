provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.name
      ManagedBy = "terraform"
    }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs      = slice(data.aws_availability_zones.available.names, 0, 3)
  services = ["accounts", "transfers", "gateway", "fraud", "notifications", "interest-batch"]
}

# ---- Network: public subnets for load balancers, private subnets for nodes and the database ----
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0"

  name            = var.name
  cidr            = "10.20.0.0/16"
  azs             = local.azs
  public_subnets  = ["10.20.0.0/22", "10.20.4.0/22", "10.20.8.0/22"]
  private_subnets = ["10.20.32.0/19", "10.20.64.0/19", "10.20.96.0/19"]

  enable_nat_gateway = true
  single_nat_gateway = true # one NAT gateway (about US$35 a month) instead of one per zone: fine for a learning cluster

  public_subnet_tags  = { "kubernetes.io/role/elb" = 1 }
  private_subnet_tags = { "kubernetes.io/role/internal-elb" = 1 }
}

# ---- Kubernetes: EKS with one managed node group ----
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0"

  name               = var.name
  kubernetes_version = "1.35"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  endpoint_public_access                   = true # restrict to your office or VPN ranges in a real bank
  enable_cluster_creator_admin_permissions = true

  addons = {
    coredns                = {}
    kube-proxy             = {}
    vpc-cni                = { before_compute = true }
    eks-pod-identity-agent = { before_compute = true }
    metrics-server         = {}
  }

  eks_managed_node_groups = {
    default = {
      instance_types = ["t3.large"] # 2 vCPU, 8 GiB: room for every ITL Pay service
      capacity_type  = "SPOT"       # much cheaper; acceptable for learning, not for production money
      min_size       = 2
      max_size       = 3
      desired_size   = 2
    }
  }
}

# ---- PostgreSQL: Amazon RDS, reachable only from the cluster's nodes ----
resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "db" {
  name   = "${var.name}-db"
  vpc_id = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_nodes" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_db_instance" "postgres" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = "18"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_encrypted = true

  username                    = "itlpay_admin"
  manage_master_user_password = true # RDS keeps the password in AWS Secrets Manager; it never appears in state or code

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period = 7
  skip_final_snapshot     = true # learning environment only: production keeps a final snapshot and deletion protection
  deletion_protection     = false
}

# ---- Object storage: customer statements, private and versioned ----
resource "aws_s3_bucket" "statements" {
  bucket_prefix = "${var.name}-statements-"
  force_destroy = true # learning environment only
}

resource "aws_s3_bucket_public_access_block" "statements" {
  bucket                  = aws_s3_bucket.statements.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "statements" {
  bucket = aws_s3_bucket.statements.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "statements" {
  bucket = aws_s3_bucket.statements.id
  rule {
    id     = "archive-old-statements"
    status = "Enabled"
    filter {}
    transition {
      days          = 90
      storage_class = "GLACIER_IR"
    }
  }
}

# ---- Container images: one ECR repository per service ----
resource "aws_ecr_repository" "service" {
  for_each             = toset(local.services)
  name                 = "itl-pay/${each.key}"
  image_tag_mutability = "IMMUTABLE" # a tag always means the same image
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# ---- Cost control: alert before the bill surprises you ----
resource "aws_budgets_budget" "monthly" {
  name         = var.name
  budget_type  = "COST"
  limit_amount = var.monthly_budget_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_email]
  }
}
