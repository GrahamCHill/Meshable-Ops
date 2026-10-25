provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

resource "google_project_service" "required" {
  for_each = toset([
    "compute.googleapis.com", "iam.googleapis.com", "iap.googleapis.com", "oslogin.googleapis.com",
    "sqladmin.googleapis.com", "servicenetworking.googleapis.com", "cloudresourcemanager.googleapis.com",
  ])
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}
