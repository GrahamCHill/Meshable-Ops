module "bootstrap" {
  data_disk_device    = "/dev/disk/by-id/google-${var.name}-services-data"
  depends_on          = [google_sql_user.admin]
  source              = "../modules/split-stack"
  repository_url      = var.repository_url
  repository_ref      = var.repository_ref
  private_ips         = local.private_ips
  postgres_ca         = google_sql_database_instance.app.server_ca_cert[0].cert
  storage_environment = { APP_SEED_STORAGE_PROVIDER = "NONE" }
  common_environment = {
    APP_DOMAIN          = var.app_domain
    ACME_EMAIL          = var.acme_email
    POSTGRES_HOST       = google_sql_database_instance.app.private_ip_address
    POSTGRES_ADMIN_USER = "meshable_admin"
    POSTGRES_SSL_MODE   = "verify-ca"

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
    secrets_encryption_key     = var.secrets_encryption_key
    document_render_token      = var.document_render_token
    plugin_runner_token        = var.plugin_runner_token
  }
}
