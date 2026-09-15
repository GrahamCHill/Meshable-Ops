variable "name" {
  type    = string
  default = "contract-management"
}
variable "aws_region" {
  type    = string
  default = "af-south-1"
}
variable "instance_type" {
  type    = string
  default = "t3.large"
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
  type        = string
  description = "CIDR allowed to SSH to the VM."
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
