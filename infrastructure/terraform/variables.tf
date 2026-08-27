variable "region" {
  description = "AWS region"
  default     = "ap-south-1"
}

variable "environment" {
  description = "staging | prod"
  default     = "staging"
}

variable "vpc_id" { description = "VPC hosting the cluster" }
variable "private_subnet_ids" { description = "Private subnets for managed services" }
variable "cluster_cidr_blocks" { description = "EKS cluster CIDRs allowed to reach services" }

variable "db_username" { default = "recoverai" }
variable "db_password" { sensitive = true }
variable "db_instance_class" { default = "db.t4g.large" }
variable "db_storage_gb" { default = 100 }

variable "redis_node_type" { default = "cache.t4g.small" }

variable "kafka_broker_type" { default = "kafka.t3.small" }
variable "kafka_storage_gb" { default = 100 }
