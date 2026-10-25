resource "google_compute_network" "app" {
  depends_on                      = [google_project_service.required]
  name                            = "${var.name}-vpc"
  auto_create_subnetworks         = false
  delete_default_routes_on_create = true
}

resource "google_compute_subnetwork" "app" {
  name                     = "${var.name}-app"
  region                   = var.region
  network                  = google_compute_network.app.id
  ip_cidr_range            = var.subnet_cidr
  private_ip_google_access = true
}

resource "google_compute_route" "internet" {
  name             = "${var.name}-internet"
  network          = google_compute_network.app.id
  dest_range       = "0.0.0.0/0"
  next_hop_gateway = "default-internet-gateway"
  priority         = 1000
}

resource "google_compute_address" "app" {
  depends_on   = [google_project_service.required]
  name         = "${var.name}-web"
  region       = var.region
  address_type = "EXTERNAL"
  network_tier = "PREMIUM"
}
