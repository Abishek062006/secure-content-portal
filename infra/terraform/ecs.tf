resource "aws_ecr_repository" "api" {
  name                 = "${var.name_prefix}-api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 15 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 15 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name_prefix}-api"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# ---- IAM ----------------------------------------------------------------------------------------------------------

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Used by ECS itself to pull the image, write logs and inject secrets into the container.
resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = compact([
      aws_secretsmanager_secret.app.arn,
      aws_db_instance.main.master_user_secret[0].secret_arn,
      var.enable_queue ? aws_secretsmanager_secret.mq[0].arn : "",
    ])
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# What the running app itself may do: read and write its own content bucket. No keys are stored; the SDK picks this up.
resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

data "aws_iam_policy_document" "task_s3" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:AbortMultipartUpload",
      "s3:ListMultipartUploadParts",
    ]
    resources = ["${aws_s3_bucket.content.arn}/*"]
  }
  statement {
    actions   = ["s3:ListBucket", "s3:ListBucketMultipartUploads", "s3:GetBucketLocation"]
    resources = [aws_s3_bucket.content.arn]
  }
}

resource "aws_iam_role_policy" "task_s3" {
  name   = "content-bucket"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_s3.json
}

# ---- Task and service ---------------------------------------------------------------------------------------------

locals {
  site_url = "https://${aws_cloudfront_distribution.site.domain_name}"

  app_environment = merge(
    {
      SPRING_PROFILES_ACTIVE = "prod"
      DB_URL                 = "jdbc:mysql://${aws_db_instance.main.address}:3306/${local.db_name}?sslMode=REQUIRED&serverTimezone=UTC"
      DB_USERNAME            = local.db_username
      DB_POOL_SIZE           = tostring(var.db_pool_size)
      STORAGE_PROVIDER       = "s3"
      STORAGE_BUCKET         = aws_s3_bucket.content.bucket
      STORAGE_REGION         = var.region
      STORAGE_PATH_STYLE     = "false"
      STORAGE_CREATE_BUCKET  = "false"
      APP_FRONTEND_URL       = local.site_url
      APP_OAUTH_REDIRECT_URI = "${local.site_url}/login/oauth2/code/google"
      APP_ADMIN_EMAILS       = var.admin_emails
      AI_BASE_URL            = var.ai_base_url
      AI_MODEL               = var.ai_model
      TRANSCODE_ENABLED      = "true"
      REDIS_ENABLED          = tostring(var.enable_redis)
      QUEUE_ENABLED          = tostring(var.enable_queue)
    },
    var.enable_redis ? {
      REDIS_HOST = local.redis_host
      REDIS_PORT = "6379"
      REDIS_SSL  = "true"
    } : {},
    var.enable_queue ? {
      RABBITMQ_HOST = local.mq_host
      RABBITMQ_PORT = "5671"
      RABBITMQ_SSL  = "true"
    } : {},
  )

  app_secrets = merge(
    {
      DB_PASSWORD          = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::"
      GOOGLE_CLIENT_ID     = "${aws_secretsmanager_secret.app.arn}:google_client_id::"
      GOOGLE_CLIENT_SECRET = "${aws_secretsmanager_secret.app.arn}:google_client_secret::"
      APP_TICKET_SECRET    = "${aws_secretsmanager_secret.app.arn}:ticket_secret::"
      AI_API_KEY           = "${aws_secretsmanager_secret.app.arn}:ai_api_key::"
    },
    var.enable_queue ? {
      RABBITMQ_USERNAME = "${aws_secretsmanager_secret.mq[0].arn}:username::"
      RABBITMQ_PASSWORD = "${aws_secretsmanager_secret.mq[0].arn}:password::"
    } : {},
  )
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  # Graviton: about 20% cheaper than x86 for the same size. The deploy script builds the image for arm64.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  ephemeral_storage {
    size_in_gib = var.task_ephemeral_storage_gib
  }

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [for k, v in local.app_environment : { name = k, value = v }]
    secrets     = [for k, v in local.app_secrets : { name = k, valueFrom = v }]

    # Room for Spring's graceful shutdown (30s) before ECS force-kills the container.
    stopTimeout = 60

    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/healthz || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "api"
      }
    }
  }])
}

resource "aws_ecs_service" "api" {
  name            = "${var.name_prefix}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.min_tasks
  launch_type     = "FARGATE"

  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # A bad release that never becomes healthy is rolled back automatically instead of taking the site down.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener_rule.from_cloudfront]

  lifecycle {
    ignore_changes = [desired_count] # auto scaling owns the count
  }
}

# ---- Auto scaling -------------------------------------------------------------------------------------------------

resource "aws_appautoscaling_target" "api" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_tasks
  max_capacity       = var.max_tasks
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name_prefix}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 55
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "requests" {
  name               = "${var.name_prefix}-requests"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 600
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.app.arn_suffix}/${aws_lb_target_group.app.arn_suffix}"
    }
  }
}
