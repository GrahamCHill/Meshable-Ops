resource "google_compute_instance" "app" {
  # Install/build needs the explicit internet route and web firewall in place.
  depends_on = [google_compute_route.internet, google_compute_firewall.web, google_compute_firewall.iap_ssh]

  name                = var.name
  zone                = var.zone
  machine_type        = var.machine_type
  deletion_protection = var.deletion_protection

  lifecycle {
    precondition {
      condition     = startswith(var.zone, "${var.region}-")
      error_message = "The zone must belong to the configured region."
    }
  }

  boot_disk {
    auto_delete = false
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      size  = var.boot_disk_size_gb
      type  = "pd-balanced"
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.app.id
    network_ip = local.private_ips.app
    access_config {
      nat_ip       = google_compute_address.app.address
      network_tier = "PREMIUM"
    }
  }

  service_account {
    email  = google_service_account.app.email
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
    user-data                = module.bootstrap.cloud_init["app"]
  }

  labels = { application = var.name }
}
