data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  cluster_name = "${var.project_name}-${var.environment}"

  selected_availability_zones = length(var.availability_zones) > 0 ? var.availability_zones : slice(data.aws_availability_zones.available.names, 0, 3)
  public_subnet_cidrs         = length(var.public_subnet_cidrs) > 0 ? var.public_subnet_cidrs : [for index in range(length(local.selected_availability_zones)) : cidrsubnet(var.vpc_cidr, 4, index + 8)]
  private_subnet_cidrs        = length(var.private_subnet_cidrs) > 0 ? var.private_subnet_cidrs : [for index in range(length(local.selected_availability_zones)) : cidrsubnet(var.vpc_cidr, 4, index)]

  container_repositories = [
    "service-registry",
    "api-gateway",
    "auth-service",
    "user-service",
    "category-service",
    "product-service",
    "cart-service",
    "order-service",
    "notification-service",
    "web",
  ]

  runtime_secret_env_names = [
    "AUTH_MONGODB_URI",
    "USER_MONGODB_URI",
    "CATEGORY_MONGODB_URI",
    "PRODUCT_MONGODB_URI",
    "CART_MONGODB_URI",
    "ORDER_MONGODB_URI",
    "JWT_SIGNING_SECRET",
    "INTERNAL_SERVICE_TOKEN",
    "RABBITMQ_USERNAME",
    "RABBITMQ_PASSWORD",
    "NOTIFICATION_MAIL_USERNAME",
    "NOTIFICATION_MAIL_PASSWORD",
    "AUTH_SERVICE_USERNAME",
    "AUTH_SERVICE_PASSWORD",
    "USER_SERVICE_USERNAME",
    "USER_SERVICE_PASSWORD",
    "CATEGORY_SERVICE_USERNAME",
    "CATEGORY_SERVICE_PASSWORD",
    "PRODUCT_SERVICE_USERNAME",
    "PRODUCT_SERVICE_PASSWORD",
    "CART_SERVICE_USERNAME",
    "CART_SERVICE_PASSWORD",
  ]

  runtime_secrets = {
    for env_name in local.runtime_secret_env_names :
    env_name => {
      name        = "${var.project_name}/${var.environment}/${lower(replace(env_name, "_", "-"))}"
      description = "Runtime secret ${env_name} for ${var.project_name} ${var.environment}."
    }
  }
}
