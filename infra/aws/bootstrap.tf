module "bootstrap" {
  data_disk_device = "ebs:${aws_ebs_volume.services.id}"
  source           = "../modules/split-stack"
  repository_url   = var.repository_url
  repository_ref   = var.repository_ref
  private_ips      = local.private_ips
  storage_environment = {
    APP_SEED_STORAGE_PROVIDER  = "S3"
    APP_SEED_STORAGE_BUCKET    = aws_s3_bucket.documents.id
    APP_SEED_STORAGE_REGION    = var.aws_region
    APP_SEED_STORAGE_ROOT_PATH = var.storage_root_path
  }
  common_environment = {
    APP_DOMAIN          = var.app_domain
    ACME_EMAIL          = var.acme_email
    POSTGRES_HOST       = aws_db_instance.app.address
    POSTGRES_ADMIN_USER = "meshable_admin"
    POSTGRES_SSL_MODE   = "verify-full"
    POSTGRES_CA_URL     = "https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem"
  }
  secrets = {
    jwt_secret                 = var.jwt_secret
    superset_secret_key        = var.superset_secret_key
    guest_token_jwt_secret     = var.guest_token_jwt_secret
    superset_admin_password    = var.superset_admin_password
    bootstrap_users_json       = var.bootstrap_users_json
    postgres_admin_password    = var.postgres_admin_password
    postgres_password          = var.postgres_password
    superset_database_password = var.superset_database_password
    mongo_password             = var.mongo_password
    secrets_encryption_key     = var.secrets_encryption_key
    document_render_token      = var.document_render_token
    plugin_runner_token        = var.plugin_runner_token
  }
}
