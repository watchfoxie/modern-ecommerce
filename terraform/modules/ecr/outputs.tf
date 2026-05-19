output "repository_urls" {
  description = "ECR repository URLs keyed by service name."
  value       = { for name, repository in aws_ecr_repository.this : name => repository.repository_url }
}
