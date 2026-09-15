variable "name" {
  type    = string
  default = "contract-management"
}
variable "location" {
  type    = string
  default = "southafricanorth"
}
variable "vm_size" {
  type    = string
  default = "Standard_D4s_v5"
}
variable "repository_url" {
  type = string
}
variable "repository_ref" {
  type    = string
  default = "main"
}
variable "app_domain" {
  type = string
}
variable "acme_email" {
  type = string
}
variable "ssh_public_key" {
  type = string
}
variable "admin_cidr" {
  type = string
}
variable "storage_root_path" {
  type    = string
  default = "contract-management/documents"
}
variable "jwt_secret" {
  type      = string
  sensitive = true
}
variable "superset_secret_key" {
  type      = string
  sensitive = true
}
variable "guest_token_jwt_secret" {
  type      = string
  sensitive = true
}
variable "superset_admin_password" {
  type      = string
  sensitive = true
}
variable "bootstrap_users_json" {
  type      = string
  sensitive = true
}
