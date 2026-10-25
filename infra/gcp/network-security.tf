resource "google_compute_firewall" "web" {
  name                    = "${var.name}-web"
  network                 = google_compute_network.app.id
  direction               = "INGRESS"
  source_ranges           = ["0.0.0.0/0"]
  target_service_accounts = [google_service_account.app.email]
  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }
}

resource "google_compute_firewall" "iap_ssh" {
  name                    = "${var.name}-iap-ssh"
  network                 = google_compute_network.app.id
  direction               = "INGRESS"
  source_ranges           = ["35.235.240.0/20"]
  target_service_accounts = concat([google_service_account.app.email], [for account in google_service_account.private : account.email])
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}

# The custom VPC retains Google's implied deny-ingress / allow-egress rules.
# No default-network rules or broad intra-subnet ingress are copied.
