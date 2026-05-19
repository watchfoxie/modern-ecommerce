locals {
  name = "${var.project_name}-${var.environment}"
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_policy_document" "logs_key" {
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
      values   = ["arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/${var.project_name}/${var.environment}/application*"]
    }
  }
}

resource "aws_kms_key" "logs" {
  description             = "KMS key for ${local.name} application logs."
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.logs_key.json
}

resource "aws_kms_alias" "logs" {
  name          = "alias/${local.name}-logs"
  target_key_id = aws_kms_key.logs.key_id
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/${var.project_name}/${var.environment}/application"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${local.name}-operations"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "text"
        x      = 0
        y      = 0
        width  = 24
        height = 2
        properties = {
          markdown = "# ${local.name}\nOperational dashboard placeholder for application, ingress, queue and cluster signals."
        }
      }
    ]
  })
}
