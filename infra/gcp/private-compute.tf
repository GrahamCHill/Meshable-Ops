variable "service_machine_type" {
  type    = string
  default = "e2-standard-4"
}
resource "google_service_account" "private" {
  for_each     = toset(["services", "runtime"])
  depends_on   = [google_project_service.required]
  account_id   = "${var.name}-${each.key}"
  display_name = "Meshable Ops ${each.key} (no project roles)"
}
resource "google_compute_instance" "private" {
  for_each            = toset(["services", "runtime"])
  depends_on          = [google_compute_router_nat.private, google_compute_firewall.private, google_compute_firewall.iap_ssh]
  name                = "${var.name}-${each.key}"
  zone                = var.zone
  machine_type        = var.service_machine_type
  deletion_protection = var.deletion_protection
  boot_disk {
    auto_delete = false
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      size  = 100
      type  = "pd-balanced"
    }
  }
  dynamic "attached_disk" {
    for_each = each.key == "services" ? [1] : []
    content {
      source      = google_compute_disk.services.id
      device_name = "${var.name}-services-data"
    }
  }
  network_interface {
    subnetwork = google_compute_subnetwork.private.id
    network_ip = local.private_ips[each.key]
  }
  service_account {
    email  = google_service_account.private[each.key].email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }
  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }
  metadata = {
    enable-oslogin           = "TRUE"
    block-project-ssh-keys   = "TRUE"
    serial-port-enable       = "FALSE"
    disable-legacy-endpoints = "TRUE"
    user-data                = module.bootstrap.cloud_init[each.key]
  }
}
