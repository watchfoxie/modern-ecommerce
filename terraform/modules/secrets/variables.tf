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
