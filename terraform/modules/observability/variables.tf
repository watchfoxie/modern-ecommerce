variable "project_name" {
  description = "Project prefix used for observability resources."
  type        = string
}

variable "environment" {
  description = "Environment suffix used for observability resources."
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 30
}
