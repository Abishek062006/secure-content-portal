output "site_url" {
  description = "Open this in the browser. The whole app lives here."
  value       = local.site_url
}

output "google_oauth_redirect_uri" {
  description = "Add this under 'Authorized redirect URIs' in your Google OAuth client, and the site_url under 'Authorized JavaScript origins'."
  value       = "${local.site_url}/login/oauth2/code/google"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.api.name
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "content_bucket" {
  value = aws_s3_bucket.content.bucket
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.site.id
}

output "database_endpoint" {
  value = aws_db_instance.main.address
}

output "region" {
  value = var.region
}

output "log_group" {
  value = aws_cloudwatch_log_group.api.name
}
