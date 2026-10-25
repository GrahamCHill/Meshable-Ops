output "public_ip" {
  value = google_compute_address.app.address
}

output "private_ip" {
  value = google_compute_instance.app.network_interface[0].network_ip
}

output "application_url" {
  value = "https://${var.app_domain}"
}

output "instance_name" {
  value = google_compute_instance.app.name
}

output "network_id" {
  value = google_compute_network.app.id
}

output "ssh_command" {
  value = "gcloud compute ssh ${google_compute_instance.app.name} --project=${var.project_id} --zone=${var.zone} --tunnel-through-iap"
}
output "private_hosts" {
  value = local.private_ips
}

output "database_host" {
  value = google_sql_database_instance.app.private_ip_address
}

output "instance_names" {
  value = merge({ app = google_compute_instance.app.name }, { for role, vm in google_compute_instance.private : role => vm.name })
}

output "database_ca_certificate" {
  description = "Public instance CA for verifying Cloud SQL during certificate rotation."
  value       = google_sql_database_instance.app.server_ca_cert[0].cert
  sensitive   = true
}

