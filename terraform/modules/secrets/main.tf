data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_policy_document" "secrets_key" {
  #checkov:skip=CKV_AWS_109:KMS key policies require Resource "*" and are constrained by account plus Secrets Manager service conditions.
  #checkov:skip=CKV_AWS_111:KMS key policies require Resource "*" and do not grant unconstrained data-plane access outside the key policy scope.
  #checkov:skip=CKV_AWS_356:KMS key policies require Resource "*" because the policy is attached to the key itself.

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
    sid    = "AllowSecretsManagerUse"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["secretsmanager.${data.aws_region.current.name}.amazonaws.com"]
    }

    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey*",
      "kms:ReEncrypt*",
    ]

    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:CallerAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${data.aws_region.current.name}.amazonaws.com"]
    }
  }
}

resource "aws_kms_key" "this" {
  description             = "KMS key for ${var.project_name} ${var.environment} runtime secrets."
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.secrets_key.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.project_name}-${var.environment}-secrets"
  target_key_id = aws_kms_key.this.key_id
}

resource "aws_secretsmanager_secret" "this" {
  for_each = var.secrets

  name                    = each.value.name
  description             = each.value.description
  kms_key_id              = aws_kms_key.this.arn
  recovery_window_in_days = var.recovery_window_in_days

  tags = {
    Environment = var.environment
    EnvName     = each.key
  }
}

resource "aws_secretsmanager_secret_rotation" "this" {
  for_each = var.rotation_lambda_arn == null ? {} : var.secrets

  secret_id           = aws_secretsmanager_secret.this[each.key].id
  rotation_lambda_arn = var.rotation_lambda_arn

  rotation_rules {
    automatically_after_days = var.rotation_automatically_after_days
  }
}
