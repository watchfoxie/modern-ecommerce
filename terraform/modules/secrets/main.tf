resource "aws_kms_key" "this" {
  description             = "KMS key for ${var.project_name} ${var.environment} runtime secrets."
  deletion_window_in_days = 30
  enable_key_rotation     = true
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
