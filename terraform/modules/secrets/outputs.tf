output "secret_arns" {
  description = "Secrets Manager ARNs keyed by runtime environment variable name."
  value       = { for name, secret in aws_secretsmanager_secret.this : name => secret.arn }
}

output "kms_key_arn" {
  description = "KMS key ARN used for runtime secrets."
  value       = aws_kms_key.this.arn
}
