resource "google_compute_firewall" "private" {
  for_each                = { services = ["8088"], runtime = ["8000", "8001", "8090"] }
  name                    = "${var.name}-${each.key}-from-app"
  network                 = google_compute_network.app.id
  direction               = "INGRESS"
  source_service_accounts = [google_service_account.app.email]
  target_service_accounts = [google_service_account.private[each.key].email]
  allow {
    protocol = "tcp"
    ports    = each.value
  }
}
resource "google_compute_firewall" "runtime_callback" {
  name                    = "${var.name}-runtime-callback"
  network                 = google_compute_network.app.id
  direction               = "INGRESS"
  source_service_accounts = [google_service_account.private["runtime"].email]
  target_service_accounts = [google_service_account.app.email]
  allow {
    protocol = "tcp"
    ports    = ["8080"]
  }
}

# Cloud SQL's RFC1918 private endpoint trusts its VPC network. Restrict clients
# with egress rules too, so the execution host cannot reach PostgreSQL.
resource "google_compute_firewall" "database_clients" {
  name                    = "${var.name}-database-clients"
  network                 = google_compute_network.app.id
  direction               = "EGRESS"
  priority                = 900
  destination_ranges      = ["${var.database_peering_address}/16"]
  target_service_accounts = [google_service_account.app.email, google_service_account.private["services"].email]
  allow {
    protocol = "tcp"
    ports    = ["5432"]
  }
}
resource "google_compute_firewall" "database_deny" {
  name               = "${var.name}-database-deny-other"
  network            = google_compute_network.app.id
  direction          = "EGRESS"
  priority           = 1000
  destination_ranges = ["${var.database_peering_address}/16"]
  deny { protocol = "all" }
}
