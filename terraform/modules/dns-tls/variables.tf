variable "enabled" {
  description = "Whether DNS validation and ACM certificate provisioning are enabled."
  type        = bool
  default     = false
}

variable "zone_name" {
  description = "Route 53 hosted zone name."
  type        = string
  default     = ""
}

variable "domain_name" {
  description = "Fully qualified domain name for the web ingress."
  type        = string
  default     = ""
}
