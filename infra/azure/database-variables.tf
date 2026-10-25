variable "postgres_admin_password" {
  type      = string
  sensitive = true
  validation {
    condition = length(var.postgres_admin_password) >= 14 && length(var.postgres_admin_password) <= 128 && sum([
      for pattern in ["[A-Z]", "[a-z]", "[0-9]", "[^A-Za-z0-9]"] : length(regexall(pattern, var.postgres_admin_password)) > 0 ? 1 : 0
    ]) >= 3
    error_message = "Use 14-128 characters from at least three classes: uppercase, lowercase, numbers and symbols. The setup helper generates a compatible password."
  }
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
