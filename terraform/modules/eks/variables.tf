variable "cluster_name" {
  description = "EKS cluster name."
  type        = string
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs used by the EKS cluster and node pool."
  type        = list(string)
}

variable "cluster_security_group_id" {
  description = "Security group attached to the EKS control plane."
  type        = string
}

variable "node_instance_types" {
  description = "EC2 instance types for the default managed node group."
  type        = list(string)
}

variable "node_desired_size" {
  description = "Desired node count."
  type        = number
}

variable "node_min_size" {
  description = "Minimum node count."
  type        = number
}

variable "node_max_size" {
  description = "Maximum node count."
  type        = number
}

variable "endpoint_public_access" {
  description = "Whether the EKS API endpoint is public."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "EKS control plane log retention in days."
  type        = number
}
