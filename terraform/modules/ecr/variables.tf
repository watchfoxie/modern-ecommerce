variable "project_name" {
  description = "Project prefix used in repository names."
  type        = string
}

variable "environment" {
  description = "Environment suffix used in repository tags."
  type        = string
}

variable "repositories" {
  description = "Container repository names to create."
  type        = list(string)
}

variable "force_delete" {
  description = "Allow deletion of non-empty repositories."
  type        = bool
  default     = false
}

variable "scan_on_push" {
  description = "Enable vulnerability scan on image push."
  type        = bool
  default     = true
}

variable "immutable_tags" {
  description = "Use immutable image tags."
  type        = bool
  default     = true
}
