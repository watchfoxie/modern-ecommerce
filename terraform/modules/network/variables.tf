variable "project_name" {
  description = "Project prefix used in network resource names."
  type        = string
}

variable "environment" {
  description = "Environment suffix used in network resource names."
  type        = string
}

variable "cidr_block" {
  description = "VPC CIDR block."
  type        = string
}

variable "availability_zones" {
  description = "Availability zones used by the VPC subnets."
  type        = list(string)
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets."
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets."
  type        = list(string)
}

variable "flow_log_retention_days" {
  description = "CloudWatch retention in days for VPC flow logs."
  type        = number
  default     = 90
}
