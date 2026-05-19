output "certificate_arn" {
  description = "Validated ACM certificate ARN, or null when DNS/TLS provisioning is disabled."
  value       = length(aws_acm_certificate_validation.web) > 0 ? aws_acm_certificate_validation.web[0].certificate_arn : null
}
