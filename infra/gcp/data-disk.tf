resource "google_compute_disk" "services" {
  lifecycle { prevent_destroy = true }
  depends_on = [google_project_service.required]
  name       = "${var.name}-services-data"
  zone       = var.zone
  type       = "pd-balanced"
  size       = 128
}
