output "cluster_name" {
  description = "EKS cluster name."
  value       = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  description = "EKS API endpoint."
  value       = aws_eks_cluster.this.endpoint
}

output "cluster_certificate_authority_data" {
  description = "Base64-encoded EKS cluster certificate authority data."
  value       = aws_eks_cluster.this.certificate_authority[0].data
  sensitive   = true
}

output "oidc_provider_arn" {
  description = "OIDC provider ARN used by Kubernetes service account IAM roles."
  value       = aws_iam_openid_connect_provider.cluster.arn
}

output "node_role_arn" {
  description = "Managed node group IAM role ARN."
  value       = aws_iam_role.node.arn
}
