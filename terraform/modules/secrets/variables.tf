variable "project_name" {
  description = "Project prefix used for secret resource names."
  type        = string
}

variable "environment" {
  description = "Environment suffix used for secret tags."
  type        = string
}

variable "secrets" {
  description = "Secrets Manager entries keyed by runtime environment variable name."
  type = map(object({
    name        = string
    description = string
  }))
}

variable "recovery_window_in_days" {
  description = "Secrets Manager recovery window."
  type        = number
  default     = 30
}

variable "rotation_lambda_arn" {
  description = "Optional Lambda ARN used to enable automatic rotation for all runtime secrets."
  type        = string
  default     = null
}

variable "rotation_automatically_after_days" {
  description = "Number of days between automatic Secrets Manager rotations when rotation_lambda_arn is configured."
  type        = number
  default     = 30
}
