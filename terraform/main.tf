module "network" {
  source = "./modules/network"

  project_name            = var.project_name
  environment             = var.environment
  cidr_block              = var.vpc_cidr
  availability_zones      = local.selected_availability_zones
  public_subnet_cidrs     = local.public_subnet_cidrs
  private_subnet_cidrs    = local.private_subnet_cidrs
  flow_log_retention_days = var.log_retention_days
}

module "ecr" {
  source = "./modules/ecr"

  project_name   = var.project_name
  environment    = var.environment
  repositories   = local.container_repositories
  force_delete   = var.force_delete_repositories
  scan_on_push   = true
  immutable_tags = true
}

module "secrets" {
  source = "./modules/secrets"

  project_name = var.project_name
  environment  = var.environment
  secrets      = local.runtime_secrets
}

module "eks" {
  source = "./modules/eks"

  cluster_name              = local.cluster_name
  kubernetes_version        = var.kubernetes_version
  private_subnet_ids        = module.network.private_subnet_ids
  cluster_security_group_id = module.network.cluster_security_group_id
  node_instance_types       = var.node_instance_types
  node_desired_size         = var.node_desired_size
  node_min_size             = var.node_min_size
  node_max_size             = var.node_max_size
  endpoint_public_access    = var.eks_endpoint_public_access
  endpoint_private_access   = var.eks_endpoint_private_access
  public_access_cidrs       = var.eks_endpoint_public_access_cidrs
  log_retention_days        = var.log_retention_days
}

module "dns_tls" {
  source = "./modules/dns-tls"

  enabled     = var.enable_dns_and_tls
  zone_name   = var.zone_name
  domain_name = var.public_host
}

module "observability" {
  source = "./modules/observability"

  project_name       = var.project_name
  environment        = var.environment
  log_retention_days = var.log_retention_days
}
