output "cluster_name" {
  description = "EKS cluster name used by Helm deployment automation."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Private EKS API server endpoint."
  value       = module.eks.cluster_endpoint
  sensitive   = true
}

output "ecr_repository_urls" {
  description = "Container registry URLs keyed by service image name."
  value       = module.ecr.repository_urls
}

output "runtime_secret_arns" {
  description = "AWS Secrets Manager ARNs keyed by runtime environment variable name."
  value       = module.secrets.secret_arns
}

output "web_certificate_arn" {
  description = "ACM certificate ARN for the public web ingress, when DNS/TLS provisioning is enabled."
  value       = module.dns_tls.certificate_arn
}
