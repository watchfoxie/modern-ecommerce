variable "project_name" {
  description = "Stable project prefix used for cloud resource names."
  type        = string
  default     = "modern-ecommerce"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "production"

  validation {
    condition     = contains(["development", "staging", "production"], var.environment)
    error_message = "environment must be one of: development, staging, production."
  }
}

variable "aws_region" {
  description = "AWS region for the production infrastructure."
  type        = string
  default     = "eu-central-1"
}

variable "vpc_cidr" {
  description = "CIDR block allocated to the application VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used for public and private subnets. Leave empty to use the first three available zones in the selected region."
  type        = list(string)
  default     = []
}

variable "public_subnet_cidrs" {
  description = "Optional explicit CIDR blocks for public subnets."
  type        = list(string)
  default     = []
}

variable "private_subnet_cidrs" {
  description = "Optional explicit CIDR blocks for private subnets."
  type        = list(string)
  default     = []
}

variable "kubernetes_version" {
  description = "EKS control plane version."
  type        = string
  default     = "1.35"
}

variable "node_instance_types" {
  description = "EC2 instance types used by the default EKS node pool."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired node count for the default EKS node pool."
  type        = number
  default     = 3
}

variable "node_min_size" {
  description = "Minimum node count for the default EKS node pool."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum node count for the default EKS node pool."
  type        = number
  default     = 6
}

variable "eks_endpoint_public_access" {
  description = "Whether to expose the EKS API endpoint publicly. Keep false unless an allowlisted operator CIDR is provided."
  type        = bool
  default     = false
}

variable "eks_endpoint_private_access" {
  description = "Whether to expose the EKS API endpoint privately inside the VPC."
  type        = bool
  default     = true
}

variable "eks_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to reach the public EKS API endpoint when public access is enabled."
  type        = list(string)
  default     = []

  validation {
    condition = alltrue([
      for cidr in var.eks_endpoint_public_access_cidrs : can(cidrhost(cidr, 0))
    ])
    error_message = "eks_endpoint_public_access_cidrs must contain valid CIDR blocks."
  }
}

variable "enable_dns_and_tls" {
  description = "When true, provisions Route 53 DNS validation records and an ACM certificate for the public host."
  type        = bool
  default     = false
}

variable "zone_name" {
  description = "Route 53 hosted zone name used when DNS and TLS provisioning is enabled."
  type        = string
  default     = ""
}

variable "public_host" {
  description = "Public host for the web ingress and TLS certificate."
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 365
}

variable "force_delete_repositories" {
  description = "Allows Terraform to delete non-empty ECR repositories in disposable environments."
  type        = bool
  default     = false
}
