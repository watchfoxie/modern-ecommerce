output "application_log_group_name" {
  description = "Application CloudWatch log group name."
  value       = aws_cloudwatch_log_group.application.name
}

output "logs_kms_key_arn" {
  description = "KMS key ARN used for application logs."
  value       = aws_kms_key.logs.arn
}
