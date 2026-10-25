variable "role" {
  type = string
  validation {
    condition     = contains(["app", "services", "runtime"], var.role)
    error_message = "Use an app, services or runtime host role."
  }
}
variable "repository_url" { type = string }
variable "repository_ref" { type = string }
variable "environment" {
  type      = map(string)
  sensitive = true
}
variable "postgres_ca" {
  type    = string
  default = ""
}
variable "data_disk_device" {
  type    = string
  default = ""
}
locals {
  environment_content = join("\n", [for key, value in var.environment : "${key}='${replace(value, "'", "\\'")}'"])
  ca_files = var.postgres_ca == "" ? [] : [{
    path = "/etc/meshable-ops/postgres-ca.pem", owner = "root:root", permissions = "0644", encoding = "b64", content = base64encode(var.postgres_ca)
  }]
  cloud_config = {
    # AWS attaches the public IP after boot; private hosts wait for NAT too.
    bootcmd        = [["sh", "-c", "for attempt in $(seq 1 90); do timeout 5 bash -c 'exec 3<>/dev/tcp/archive.ubuntu.com/80' && exit 0; sleep 5; done; exit 1"]]
    package_update = true
    packages       = ["git", "docker.io", "docker-compose-v2", "python3", "ca-certificates"]
    write_files = concat([{
      path = "/etc/meshable-ops/environment", owner = "root:root", permissions = "0600", encoding = "b64", content = base64encode("${local.environment_content}\n")
    }], local.ca_files)
    runcmd = concat([
      ["systemctl", "enable", "--now", "docker"],
      ["git", "clone", "--branch", var.repository_ref, "--", var.repository_url, "/opt/contract-app"],
      ["install", "-m", "0600", "/etc/meshable-ops/environment", "/opt/contract-app/.env.production"],
      ], var.data_disk_device == "" ? [] : [["python3", "/opt/contract-app/deploy/terraform/mount_data.py", "--device", var.data_disk_device]], [
      ["python3", "/opt/contract-app/deploy/terraform/start.py", "--role", var.role],
    ])
  }
}
output "cloud_init" {
  value     = "#cloud-config\n${yamlencode(local.cloud_config)}"
  sensitive = true
}
