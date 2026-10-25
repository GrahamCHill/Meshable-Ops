variable "database_tier" {
  type    = string
  default = "db-custom-2-7680"
}
variable "database_high_availability" {
  type    = bool
  default = true
}
variable "database_peering_address" {
  type    = string
  default = "10.45.0.0"
}
resource "google_compute_global_address" "database" {
  name          = "${var.name}-postgres-peering"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  address       = var.database_peering_address
  network       = google_compute_network.app.id
}
resource "google_service_networking_connection" "database" {
  network                 = google_compute_network.app.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.database.name]
}
resource "google_sql_database_instance" "app" {
  depends_on          = [google_service_networking_connection.database]
  name                = "${var.name}-postgres"
  region              = var.region
  database_version    = "POSTGRES_17"
  deletion_protection = true
  settings {
    tier                        = var.database_tier
    edition                     = "ENTERPRISE"
    availability_type           = var.database_high_availability ? "REGIONAL" : "ZONAL"
    disk_type                   = "PD_SSD"
    disk_size                   = 100
    disk_autoresize             = true
    deletion_protection_enabled = true
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.app.id
      ssl_mode        = "ENCRYPTED_ONLY"
      server_ca_mode  = "GOOGLE_MANAGED_INTERNAL_CA"
    }
    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7
      backup_retention_settings {
        retained_backups = 14
        retention_unit   = "COUNT"
      }
    }
  }
}
resource "google_sql_user" "admin" {
  name     = "meshable_admin"
  instance = google_sql_database_instance.app.name
  password = var.postgres_admin_password
}
