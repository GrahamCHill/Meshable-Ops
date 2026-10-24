variable "name" {
  type    = string
  default = "contract-management"
}
variable "location" {
  type    = string
  default = "southafricanorth"
}
variable "vnet_cidr" {
  type        = string
  default     = "10.40.0.0/16"
  description = "Dedicated IPv4 VNet range (/16 through /20). App/Bastion subnets are derived automatically; avoid overlap with connected networks."
  validation {
    condition     = can(cidrnetmask(var.vnet_cidr)) && try(tonumber(split("/", var.vnet_cidr)[1]) >= 16 && tonumber(split("/", var.vnet_cidr)[1]) <= 20, false)
    error_message = "Use an IPv4 VNet CIDR with a /16 through /20 prefix."
  }
}
variable "enable_bastion" {
  type        = bool
  default     = false
  description = "Provision a billed Standard Azure Bastion and its dedicated subnet/public IP for private VM administration without an existing VPN."
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
  type        = string
  default     = null
  description = "Optional trusted IPv4 SSH source over existing private connectivity, such as a connected VPN subnet. Otherwise enable Bastion."
  validation {
    condition     = var.admin_cidr == null ? true : can(cidrnetmask(var.admin_cidr)) && try(tonumber(split("/", var.admin_cidr)[1]) > 0, false)
    error_message = "Use a restricted IPv4 CIDR for private SSH; 0.0.0.0/0 is not allowed."
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
