data "aws_iam_policy_document" "cluster_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_policy_document" "cluster_key" {
  statement {
    sid    = "AllowAccountAdministration"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }

    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowCloudWatchLogsUse"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["logs.${data.aws_region.current.name}.amazonaws.com"]
    }

    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]

    resources = ["*"]

    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/eks/${var.cluster_name}/cluster*"]
    }
  }
}

data "aws_iam_policy_document" "node_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_kms_key" "cluster" {
  description             = "KMS key for EKS secrets and control plane logs in ${var.cluster_name}."
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.cluster_key.json
}

resource "aws_kms_alias" "cluster" {
  name          = "alias/${var.cluster_name}-eks"
  target_key_id = aws_kms_key.cluster.key_id
}

resource "aws_cloudwatch_log_group" "cluster" {
  name              = "/aws/eks/${var.cluster_name}/cluster"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.cluster.arn
}

resource "aws_iam_role" "cluster" {
  name               = "${var.cluster_name}-eks-cluster"
  assume_role_policy = data.aws_iam_policy_document.cluster_assume_role.json
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role" "node" {
  name               = "${var.cluster_name}-eks-node"
  assume_role_policy = data.aws_iam_policy_document.node_assume_role.json
}

resource "aws_iam_role_policy_attachment" "node_worker" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  enabled_cluster_log_types = [
    "api",
    "audit",
    "authenticator",
    "controllerManager",
    "scheduler",
  ]

  encryption_config {
    resources = ["secrets"]

    provider {
      key_arn = aws_kms_key.cluster.arn
    }
  }

  vpc_config {
    endpoint_private_access = true
    endpoint_public_access  = var.endpoint_public_access
    security_group_ids      = [var.cluster_security_group_id]
    subnet_ids              = var.private_subnet_ids
  }

  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = true
  }

  depends_on = [
    aws_cloudwatch_log_group.cluster,
    aws_iam_role_policy_attachment.cluster_policy,
  ]
}

resource "aws_launch_template" "node" {
  name_prefix            = "${var.cluster_name}-node-"
  update_default_version = true

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = true
      volume_size           = 50
      volume_type           = "gp3"
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 2
    http_tokens                 = "required"
  }

  tag_specifications {
    resource_type = "instance"

    tags = {
      Name = "${var.cluster_name}-node"
    }
  }
}

resource "aws_eks_node_group" "default" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.cluster_name}-default"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = var.node_instance_types
  capacity_type   = "ON_DEMAND"

  scaling_config {
    desired_size = var.node_desired_size
    max_size     = var.node_max_size
    min_size     = var.node_min_size
  }

  update_config {
    max_unavailable = 1
  }

  launch_template {
    id      = aws_launch_template.node.id
    version = "$Latest"
  }

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr,
  ]
}

data "tls_certificate" "cluster" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "cluster" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.cluster.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
}
