variable "repository_url" { type = string }
variable "repository_ref" { type = string }
variable "common_environment" { type = map(string) }
variable "private_ips" { type = map(string) }
variable "storage_environment" {
  type    = map(string)
  default = { APP_SEED_STORAGE_PROVIDER = "NONE" }
}
variable "postgres_ca" {
  type    = string
  default = ""
}
variable "data_disk_device" {
  type    = string
  default = ""
}
variable "secrets" {
  type      = map(string)
  sensitive = true
}
locals {
  environments = {
    app = merge(var.storage_environment, {
      JWT_SECRET                  = var.secrets.jwt_secret
      POSTGRES_PASSWORD           = var.secrets.postgres_password
      MONGO_PASSWORD              = var.secrets.mongo_password
      APP_SECRETS_ENCRYPTION_KEY  = var.secrets.secrets_encryption_key
      SUPERSET_ADMIN_PASSWORD     = var.secrets.superset_admin_password
      APP_DOCUMENT_RENDER_TOKEN   = var.secrets.document_render_token
      APP_PLUGIN_RUNNER_TOKEN     = var.secrets.plugin_runner_token
      APP_BOOTSTRAP_USERS_JSON    = jsonencode(jsondecode(var.secrets.bootstrap_users_json))
      APP_MFA_PRIMARY_ADMIN_EMAIL = jsondecode(var.secrets.bootstrap_users_json)[0].email
      APP_PUBLIC_BASE_URL         = "https://${var.common_environment.APP_DOMAIN}"
      APP_SEED_STORAGE_FORCE      = "false"
    })
    services = {
      POSTGRES_ADMIN_PASSWORD    = var.secrets.postgres_admin_password
      POSTGRES_PASSWORD          = var.secrets.postgres_password
      SUPERSET_DATABASE_PASSWORD = var.secrets.superset_database_password
      MONGO_PASSWORD             = var.secrets.mongo_password
      SUPERSET_SECRET_KEY        = var.secrets.superset_secret_key
      GUEST_TOKEN_JWT_SECRET     = var.secrets.guest_token_jwt_secret
      SUPERSET_ADMIN_PASSWORD    = var.secrets.superset_admin_password
      SUPERSET_ADMIN_EMAIL       = jsondecode(var.secrets.bootstrap_users_json)[0].email
    }
    runtime = {
      APP_DOCUMENT_RENDER_TOKEN = var.secrets.document_render_token
      APP_PLUGIN_RUNNER_TOKEN   = var.secrets.plugin_runner_token
    }
  }
}
module "host" {
  for_each         = toset(["app", "services", "runtime"])
  source           = "../host-bootstrap"
  role             = each.key
  repository_url   = var.repository_url
  repository_ref   = var.repository_ref
  postgres_ca      = each.key == "runtime" ? "" : var.postgres_ca
  data_disk_device = each.key == "services" ? var.data_disk_device : ""
  environment = merge(var.common_environment, local.environments[each.key], {
    HOST_PRIVATE_IP     = var.private_ips[each.key]
    APP_PRIVATE_IP      = var.private_ips.app
    SERVICES_PRIVATE_IP = var.private_ips.services
    RUNTIME_PRIVATE_IP  = var.private_ips.runtime
  })
}
output "cloud_init" {
  value     = { for role, host in module.host : role => host.cloud_init }
  sensitive = true
}
