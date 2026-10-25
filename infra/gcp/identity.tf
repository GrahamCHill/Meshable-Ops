resource "google_service_account" "app" {
  depends_on   = [google_project_service.required]
  account_id   = var.name
  display_name = "Meshable Ops VM (no project roles)"
}

resource "google_iap_tunnel_instance_iam_member" "admin" {
  for_each = var.admin_members
  project  = var.project_id
  zone     = var.zone
  instance = google_compute_instance.app.name
  role     = "roles/iap.tunnelResourceAccessor"
  member   = each.value
  condition {
    title      = "ssh-only"
    expression = "destination.port == 22"
  }
}

resource "google_compute_instance_iam_member" "admin" {
  for_each      = var.admin_members
  project       = var.project_id
  zone          = var.zone
  instance_name = google_compute_instance.app.name
  role          = "roles/compute.osAdminLogin"
  member        = each.value
}

resource "google_service_account_iam_member" "admin" {
  for_each           = var.admin_members
  service_account_id = google_service_account.app.name
  role               = "roles/iam.serviceAccountUser"
  member             = each.value
}

# gcloud SSH needs project/instance discovery as well as instance-scoped login.
resource "google_project_iam_member" "admin_discovery" {
  for_each = var.admin_members
  project  = var.project_id
  role     = "roles/compute.viewer"
  member   = each.value
}

locals {
  private_admins = { for pair in setproduct(toset(["services", "runtime"]), var.admin_members) : "${pair[0]}:${pair[1]}" => { role = pair[0], member = pair[1] } }
}
resource "google_iap_tunnel_instance_iam_member" "private_admin" {
  for_each = local.private_admins
  project  = var.project_id
  zone     = var.zone
  instance = google_compute_instance.private[each.value.role].name
  role     = "roles/iap.tunnelResourceAccessor"
  member   = each.value.member
  condition {
    title      = "ssh-only"
    expression = "destination.port == 22"
  }
}
resource "google_compute_instance_iam_member" "private_admin" {
  for_each      = local.private_admins
  project       = var.project_id
  zone          = var.zone
  instance_name = google_compute_instance.private[each.value.role].name
  role          = "roles/compute.osAdminLogin"
  member        = each.value.member
}
resource "google_service_account_iam_member" "private_admin" {
  for_each           = local.private_admins
  service_account_id = google_service_account.private[each.value.role].name
  role               = "roles/iam.serviceAccountUser"
  member             = each.value.member
}
