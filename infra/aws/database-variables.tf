variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
variable "postgres_password" {
  type      = string
  sensitive = true
}
variable "superset_database_password" {
  type      = string
  sensitive = true
}
variable "secrets_encryption_key" {
  type      = string
  sensitive = true
}
variable "document_render_token" {
  type      = string
  sensitive = true
}
variable "plugin_runner_token" {
  type      = string
  sensitive = true
}
