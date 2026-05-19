output "vpc_id" {
  description = "Application VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs for ingress load balancers."
  value       = [for subnet in aws_subnet.public : subnet.id]
}

output "private_subnet_ids" {
  description = "Private subnet IDs for application nodes."
  value       = [for subnet in aws_subnet.private : subnet.id]
}

output "cluster_security_group_id" {
  description = "Security group ID attached to the EKS control plane."
  value       = aws_security_group.eks_cluster.id
}
