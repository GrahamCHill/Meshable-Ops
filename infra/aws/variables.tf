variable "name" {
  type    = string
  default = "contract-management"
}
variable "aws_region" {
  type    = string
  default = "af-south-1"
}
variable "vpc_cidr" {
  type        = string
  default     = "10.42.0.0/16"
  description = "Dedicated IPv4 VPC range (/16 through /20). The app subnet is derived automatically; choose a range that does not overlap connected networks."
  validation {
    condition     = can(cidrnetmask(var.vpc_cidr)) && try(tonumber(split("/", var.vpc_cidr)[1]) >= 16 && tonumber(split("/", var.vpc_cidr)[1]) <= 20, false)
    error_message = "Use an IPv4 VPC CIDR with a /16 through /20 prefix."
  }
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
  description = "Trusted IPv4 source CIDR allowed to SSH to the VM, usually your public IP with /32."
  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && try(tonumber(split("/", var.admin_cidr)[1]) > 0, false)
    error_message = "Use a valid restricted IPv4 CIDR for SSH; 0.0.0.0/0 is not allowed."
  }
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
