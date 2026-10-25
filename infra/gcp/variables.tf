variable "project_id" {
  type        = string
  description = "Existing Google Cloud project ID with billing enabled."
}

variable "name" {
  type    = string
  default = "meshable-ops"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,18}[a-z0-9]$", var.name))
    error_message = "Use 6-20 lowercase letters, digits or hyphens, starting with a letter and ending with a letter or digit."
  }
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "zone" {
  type    = string
  default = "us-central1-a"
  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]+-[a-z]$", var.zone))
    error_message = "Use a Google Cloud zone such as us-central1-a."
  }
}

variable "subnet_cidr" {
  type    = string
  default = "10.44.1.0/24"
  validation {
    condition     = can(cidrnetmask(var.subnet_cidr)) && try(tonumber(split("/", var.subnet_cidr)[1]) >= 16 && tonumber(split("/", var.subnet_cidr)[1]) <= 28, false)
    error_message = "Use an IPv4 subnet with a /16 through /28 prefix, without overlaps with connected networks."
  }
}

variable "machine_type" {
  type        = string
  default     = "e2-standard-4"
  description = "4 vCPU / 16 GB default allows space for the stack and image builds. Review sizing before production."
}

variable "boot_disk_size_gb" {
  type    = number
  default = 100
  validation {
    condition     = var.boot_disk_size_gb >= 80 && floor(var.boot_disk_size_gb) == var.boot_disk_size_gb
    error_message = "Allocate at least 80 whole GB for images, databases and generated files."
  }
}

variable "deletion_protection" {
  type        = bool
  default     = true
  description = "Requires an explicit reviewed update before Terraform can delete or replace the VM."
}

variable "admin_members" {
  type        = set(string)
  description = "Trusted IAM users/groups granted IAP SSH and OS Login with sudo on this VM."
  validation {
    condition     = length(var.admin_members) > 0 && alltrue([for member in var.admin_members : can(regex("^(user|group):[^[:space:]@]+@[^[:space:]@]+\\.[^[:space:]@]+$", member))])
    error_message = "Supply at least one explicit user:email or group:email administrator; public principals are not allowed."
  }
}

variable "repository_url" {
  type        = string
  description = "Public HTTPS Git clone URL; private checkout credentials are not provisioned."
  validation {
    condition     = can(regex("^https://[A-Za-z0-9.-]+/[A-Za-z0-9_./-]+$", var.repository_url))
    error_message = "Use a public HTTPS repository URL without credentials, query parameters or shell syntax."
  }
}

variable "repository_ref" {
  type    = string
  default = "main"
  validation {
    condition     = can(regex("^[A-Za-z0-9][A-Za-z0-9_./-]*$", var.repository_ref))
    error_message = "Use a branch or tag name without whitespace or shell syntax."
  }
}

variable "app_domain" {
  type = string
  validation {
    condition     = length(var.app_domain) <= 253 && can(regex("^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$", var.app_domain))
    error_message = "Use a DNS hostname without a scheme, port or path."
  }
}

variable "acme_email" {
  type = string
  validation {
    condition     = can(regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", var.acme_email))
    error_message = "Supply an email address for certificate renewal notices."
  }
}

variable "jwt_secret" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.jwt_secret) >= 32 && !can(regex("[\r\n]", var.jwt_secret))
    error_message = "Use a random JWT secret of at least 32 characters on one line."
  }
}

variable "superset_secret_key" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.superset_secret_key) >= 32 && !can(regex("[\r\n]", var.superset_secret_key))
    error_message = "Use a random Superset secret of at least 32 characters on one line."
  }
}

variable "guest_token_jwt_secret" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.guest_token_jwt_secret) >= 32 && !can(regex("[\r\n]", var.guest_token_jwt_secret))
    error_message = "Use a random guest-token secret of at least 32 characters on one line."
  }
}

variable "superset_admin_password" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.superset_admin_password) >= 14 && !can(regex("[\r\n]", var.superset_admin_password))
    error_message = "Use a Superset password of at least 14 characters on one line."
  }
}

variable "bootstrap_users_json" {
  type      = string
  sensitive = true
  validation {
    condition = try(length(jsondecode(var.bootstrap_users_json)) > 0 && alltrue([
      for user in jsondecode(var.bootstrap_users_json) :
      user.role == "SUPER_ADMIN" && length(user.password) >= 14 && length(user.email) > 3
    ]), false)
    error_message = "Supply a nonempty JSON array of SUPER_ADMIN accounts with email and a password of at least 14 characters."
  }
}
