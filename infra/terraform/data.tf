# ---- MySQL (RDS) --------------------------------------------------------------------------------------------------

locals {
  db_name     = "secureportal"
  db_username = "gradientnova"
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-db"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_db_parameter_group" "main" {
  name   = "${var.name_prefix}-mysql84"
  family = "mysql8.4"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }
  parameter {
    name  = "slow_query_log"
    value = "1"
  }
  parameter {
    name  = "long_query_time"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage_gib
  max_allocated_storage = var.db_allocated_storage_gib * 4
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = local.db_name
  username = local.db_username
  # RDS generates the password and keeps it in Secrets Manager; it never appears in this repo or in Terraform state.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period = var.db_backup_days
  backup_window           = "21:00-22:00"
  maintenance_window      = "sun:22:30-sun:23:30"
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  deletion_protection        = var.db_deletion_protection
  skip_final_snapshot        = !var.db_deletion_protection
  final_snapshot_identifier  = var.db_deletion_protection ? "${var.name_prefix}-final" : null

  performance_insights_enabled    = false # not offered on the smaller burstable classes
  enabled_cloudwatch_logs_exports = ["error", "slowquery"]
}

# ---- App secrets --------------------------------------------------------------------------------------------------

resource "random_password" "ticket_secret" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name_prefix}/app"
  description             = "Google OAuth credentials, stream-ticket signing key, AI key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    google_client_id     = var.google_client_id
    google_client_secret = var.google_client_secret
    ticket_secret        = random_password.ticket_secret.result
    ai_api_key           = var.ai_api_key
  })
}

# ---- Optional: Redis ----------------------------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "main" {
  count      = var.enable_redis ? 1 : 0
  name       = "${var.name_prefix}-cache"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_elasticache_replication_group" "main" {
  count                      = var.enable_redis ? 1 : 0
  replication_group_id       = "${var.name_prefix}-redis"
  description                = "Sessions, cache and rate limits"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main[0].name
  security_group_ids         = [aws_security_group.cache[0].id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  automatic_failover_enabled = false
  snapshot_retention_limit   = 0
}

# ---- Optional: RabbitMQ (Amazon MQ) -------------------------------------------------------------------------------

resource "random_password" "mq" {
  count   = var.enable_queue ? 1 : 0
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "mq" {
  count                   = var.enable_queue ? 1 : 0
  name                    = "${var.name_prefix}/mq"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mq" {
  count         = var.enable_queue ? 1 : 0
  secret_id     = aws_secretsmanager_secret.mq[0].id
  secret_string = jsonencode({ username = "gradientnova", password = random_password.mq[0].result })
}

resource "aws_mq_broker" "main" {
  count                      = var.enable_queue ? 1 : 0
  broker_name                = "${var.name_prefix}-rabbit"
  engine_type                = "RabbitMQ"
  engine_version             = "3.13"
  host_instance_type         = var.mq_instance_type
  deployment_mode            = "SINGLE_INSTANCE"
  publicly_accessible        = false
  auto_minor_version_upgrade = true
  subnet_ids                 = [aws_subnet.data[0].id]
  security_groups            = [aws_security_group.mq[0].id]

  user {
    username = "gradientnova"
    password = random_password.mq[0].result
  }
}

locals {
  redis_host = var.enable_redis ? aws_elasticache_replication_group.main[0].primary_endpoint_address : ""
  mq_host    = var.enable_queue ? regex("amqps://([^:/]+)", aws_mq_broker.main[0].instances[0].endpoints[0])[0] : ""
}
