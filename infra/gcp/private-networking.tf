variable "private_subnet_cidr" {
  type    = string
  default = "10.44.2.0/24"
  validation {
    condition     = can(cidrnetmask(var.private_subnet_cidr)) && try(tonumber(split("/", var.private_subnet_cidr)[1]) >= 16 && tonumber(split("/", var.private_subnet_cidr)[1]) <= 28, false)
    error_message = "Use a non-overlapping IPv4 /16 through /28 subnet."
  }
}
locals {
  private_ips = {
    app      = cidrhost(var.subnet_cidr, 10)
    services = cidrhost(var.private_subnet_cidr, 10)
    runtime  = cidrhost(var.private_subnet_cidr, 11)
  }
}
resource "google_compute_subnetwork" "private" {
  name                     = "${var.name}-private"
  region                   = var.region
  network                  = google_compute_network.app.id
  ip_cidr_range            = var.private_subnet_cidr
  private_ip_google_access = true
}
resource "google_compute_router" "private" {
  name    = "${var.name}-outbound"
  region  = var.region
  network = google_compute_network.app.id
}
resource "google_compute_router_nat" "private" {
  name                               = "${var.name}-outbound"
  router                             = google_compute_router.private.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"
  subnetwork {
    name                    = google_compute_subnetwork.private.id
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }
}
