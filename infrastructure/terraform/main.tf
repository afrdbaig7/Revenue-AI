# ============================================================================
# RecoverAI — Terraform (production extension, AWS)
#
# Managed services only (per ADR-002/003 and docs/architecture/scaling.md):
#   RDS PostgreSQL  ·  ElastiCache Redis  ·  MSK Kafka  ·  EKS (via module)
# Runbooks: docs/runbooks/database-degradation.md, kafka-consumer-lag.md
# ============================================================================
terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
  backend "s3" {
    # bucket/region/key set at init; never commit state
  }
}

provider "aws" {
  region = var.region
}

# --- PostgreSQL (system of record) -----------------------------------------
resource "aws_db_instance" "recoverai" {
  identifier          = "recoverai-${var.environment}"
  engine              = "postgres"
  engine_version      = "16.4"
  instance_class      = var.db_instance_class
  allocated_storage   = var.db_storage_gb
  storage_encrypted   = true
  db_name             = "recoverai"
  username            = var.db_username
  password            = var.db_password
  backup_retention_period = 7
  multi_az            = var.environment == "prod"
  deletion_protection = var.environment == "prod"
  skip_final_snapshot = var.environment != "prod"
  vpc_security_group_ids = [aws_security_group.recoverai_db.id]
  db_subnet_group_name = aws_db_subnet_group.recoverai.name
  monitoring_interval = 60
}

resource "aws_db_subnet_group" "recoverai" {
  name       = "recoverai-${var.environment}"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "recoverai_db" {
  name        = "recoverai-db-${var.environment}"
  description = "PostgreSQL access from the EKS cluster"
  vpc_id      = var.vpc_id
  ingress {
    from_port = 5432
    to_port   = 5432
    protocol  = "tcp"
    cidr_blocks = var.cluster_cidr_blocks
  }
}

# --- Redis (rate limits / coordination — PostgreSQL stays the system of record) ---
resource "aws_elasticache_cluster" "recoverai" {
  cluster_id           = "recoverai-${var.environment}"
  engine               = "redis"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  engine_version       = "7.1"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.recoverai.name
  security_group_ids   = [aws_security_group.recoverai_redis.id]
}

resource "aws_elasticache_subnet_group" "recoverai" {
  name       = "recoverai-${var.environment}"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "recoverai_redis" {
  name        = "recoverai-redis-${var.environment}"
  description = "Redis access from the EKS cluster"
  vpc_id      = var.vpc_id
  ingress {
    from_port = 6379
    to_port   = 6379
    protocol  = "tcp"
    cidr_blocks = var.cluster_cidr_blocks
  }
}

# --- Kafka (event backbone) --------------------------------------------------
resource "aws_msk_cluster" "recoverai" {
  cluster_name           = "recoverai-${var.environment}"
  kafka_version          = "3.7.0"
  number_of_broker_nodes = 3
  broker_node_group_info {
    instance_type = var.kafka_broker_type
    client_subnets = var.private_subnet_ids
    security_groups = [aws_security_group.recoverai_kafka.id]
    storage_info {
      ebs_storage_info { volume_size = var.kafka_storage_gb }
    }
  }
  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
    }
  }
}

resource "aws_security_group" "recoverai_kafka" {
  name        = "recoverai-kafka-${var.environment}"
  description = "Kafka access from the EKS cluster"
  vpc_id      = var.vpc_id
  ingress {
    from_port = 9092
    to_port   = 9098
    protocol  = "tcp"
    cidr_blocks = var.cluster_cidr_blocks
  }
}

# --- Kubernetes (EKS cluster via module — cluster config not duplicated here) --
# The Helm chart (infrastructure/helm/recoverai) deploys the application onto the
# existing EKS cluster; Temporal Cloud / Redpanda Cloud are external SaaS.

# --- Outputs ------------------------------------------------------------------
output "database_endpoint" {
  value = aws_db_instance.recoverai.endpoint
  sensitive = true
}
output "redis_endpoint" {
  value = aws_elasticache_cluster.recoverai.cache_nodes[0].address
  sensitive = true
}
output "kafka_bootstrap_brokers" {
  value = aws_msk_cluster.recoverai.bootstrap_brokers_tls
  sensitive = true
}
