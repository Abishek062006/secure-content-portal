variable "region" {
  description = "AWS region for the app, database and buckets. Pick the one closest to your learners."
  type        = string
  default     = "ap-south-1"
}

variable "name_prefix" {
  description = "Prefix for resource names. Lowercase letters, digits and hyphens."
  type        = string
  default     = "gradientnova"
}

# ---- Secrets and app settings -------------------------------------------------------------------------------------

variable "google_client_id" {
  description = "Google OAuth client ID (Google Cloud Console > Credentials)."
  type        = string
}

variable "google_client_secret" {
  description = "Google OAuth client secret."
  type        = string
  sensitive   = true
}

variable "admin_emails" {
  description = "Comma-separated Google emails that become admins on first sign-in. The only way to become admin."
  type        = string
}

variable "ai_api_key" {
  description = "API key for the OpenAI-compatible provider used for question generation (Groq by default). Leave empty to disable AI."
  type        = string
  default     = ""
  sensitive   = true
}

variable "ai_model" {
  type    = string
  default = ""
}

variable "ai_base_url" {
  type    = string
  default = "https://api.groq.com/openai/v1"
}

# ---- Sizing -------------------------------------------------------------------------------------------------------

variable "task_cpu" {
  description = "Fargate CPU units per task (1024 = 1 vCPU). ffmpeg packaging is CPU-heavy, so don't go below 1024."
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Fargate memory (MiB) per task."
  type        = number
  default     = 2048
}

variable "task_ephemeral_storage_gib" {
  description = "Scratch disk per task (21-200). Video packaging downloads the original and writes every rendition here."
  type        = number
  default     = 60
}

variable "min_tasks" {
  description = "Fewest running tasks. Two keeps the site up while one is replaced or fails."
  type        = number
  default     = 2
}

variable "max_tasks" {
  description = "Most tasks auto scaling may start. Keep max_tasks * db_pool_size under the database's connection limit."
  type        = number
  default     = 10
}

variable "db_pool_size" {
  description = "Database connections each task may open."
  type        = number
  default     = 10
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_allocated_storage_gib" {
  type    = number
  default = 50
}

variable "db_multi_az" {
  description = "A standby copy in a second zone with automatic failover. Roughly doubles the database cost."
  type        = bool
  default     = false
}

variable "db_backup_days" {
  type    = number
  default = 7
}

variable "db_deletion_protection" {
  description = "Stops the database being deleted by mistake. Set false before terraform destroy."
  type        = bool
  default     = false
}

# ---- Optional infrastructure --------------------------------------------------------------------------------------

variable "enable_redis" {
  description = "ElastiCache Redis: shared sessions, response cache and per-user rate limits across tasks."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "enable_queue" {
  description = "Amazon MQ (RabbitMQ): background jobs (question generation, video packaging) survive restarts and spread over tasks."
  type        = bool
  default     = false
}

variable "mq_instance_type" {
  type    = string
  default = "mq.t3.micro"
}

variable "enable_waf" {
  description = "AWS WAF in front of CloudFront: managed rule sets and a per-IP rate limit. About $10/month plus request charges."
  type        = bool
  default     = false
}

variable "alarm_email" {
  description = "Email for CloudWatch alarms (5xx errors, unhealthy tasks, database CPU/disk). Empty = alarms exist but notify nobody."
  type        = string
  default     = ""
}

variable "cloudfront_price_class" {
  description = "PriceClass_100 (US/EU), PriceClass_200 (adds Asia, Middle East, Africa) or PriceClass_All."
  type        = string
  default     = "PriceClass_200"
}

variable "force_destroy_buckets" {
  description = "Let terraform destroy delete buckets that still hold files. Leave false; set true only when tearing everything down."
  type        = bool
  default     = false
}

variable "image_tag" {
  description = "Docker image tag the service runs. scripts/deploy-aws.sh pushes 'latest' and a git-sha tag."
  type        = string
  default     = "latest"
}
