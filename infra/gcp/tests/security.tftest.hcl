mock_provider "google" {
  mock_resource "google_compute_disk" {
    defaults = { id = "projects/meshable-test-project/zones/us-central1-a/disks/meshable-ops-services-data" }
  }
  mock_resource "google_compute_network" {
    defaults = { id = "projects/meshable-test-project/global/networks/meshable-ops-vpc" }
  }
  mock_resource "google_sql_database_instance" {
    defaults = { private_ip_address = "10.45.0.2", server_ca_cert = [{ cert = "test-only-cloud-sql-ca" }] }
  }
  mock_resource "google_service_account" {
    defaults = {
      email = "meshable-ops@meshable-test-project.iam.gserviceaccount.com"
      name  = "projects/meshable-test-project/serviceAccounts/meshable-ops@meshable-test-project.iam.gserviceaccount.com"
    }
  }
  mock_resource "google_compute_address" {
    defaults = { address = "203.0.113.10" }
  }
}

variables {
  postgres_admin_password    = "test-only-postgres_admin_password-long-random-value"
  postgres_password          = "test-only-postgres_password-long-random-value"
  superset_database_password = "test-only-superset_database_password-long-random-value"
  secrets_encryption_key     = "dGVzdC1vbmx5LXdyYXBwaW5nLWtleS0zMi1ieXRlcyEh"
  document_render_token      = "test-only-document_render_token-long-random-value"
  plugin_runner_token        = "test-only-plugin_runner_token-long-random-value"
  project_id                 = "meshable-test-project"
  repository_url             = "https://github.com/example/meshable-ops.git"
  repository_ref             = "template/v1.0.0"
  app_domain                 = "workspace.example.com"
  acme_email                 = "ops@example.com"
  admin_members              = ["user:owner@example.com", "group:operators@example.com"]
  jwt_secret                 = "test-only-jwt-secret-with-at-least-32-characters"
  superset_secret_key        = "test-only-superset-secret-with-at-least-32-characters"
  guest_token_jwt_secret     = "test-only-guest-secret-with-at-least-32-characters"
  superset_admin_password    = "test-only-superset-password"
  bootstrap_users_json       = "[{\"email\":\"owner@example.com\",\"username\":\"admin\",\"password\":\"test-only-$pass'word\",\"role\":\"SUPER_ADMIN\"}]"
}

override_resource {
  target = google_service_account.private["services"]
  values = {
    email = "meshable-ops-services@meshable-test-project.iam.gserviceaccount.com"
    name  = "projects/meshable-test-project/serviceAccounts/meshable-ops-services@meshable-test-project.iam.gserviceaccount.com"
  }
}
override_resource {
  target = google_service_account.private["runtime"]
  values = {
    email = "meshable-ops-runtime@meshable-test-project.iam.gserviceaccount.com"
    name  = "projects/meshable-test-project/serviceAccounts/meshable-ops-runtime@meshable-test-project.iam.gserviceaccount.com"
  }
}

run "explicit_network_and_iap_administration" {
  command = apply

  assert {
    condition = (
      alltrue([for host in google_compute_instance.private : length(host.network_interface[0].access_config) == 0 && host.network_interface[0].subnetwork == google_compute_subnetwork.private.id]) &&
      google_compute_disk.services.size == 128 && one(google_compute_instance.private["services"].attached_disk).source == google_compute_disk.services.id &&
      length(google_compute_instance.private["runtime"].attached_disk) == 0 &&
      google_compute_router_nat.private.source_subnetwork_ip_ranges_to_nat == "LIST_OF_SUBNETWORKS" &&
      one(google_compute_router_nat.private.subnetwork).name == google_compute_subnetwork.private.id &&
      !google_sql_database_instance.app.settings[0].ip_configuration[0].ipv4_enabled &&
      google_sql_database_instance.app.settings[0].ip_configuration[0].private_network == google_compute_network.app.id &&
      google_sql_database_instance.app.settings[0].ip_configuration[0].ssl_mode == "ENCRYPTED_ONLY" &&
      google_sql_database_instance.app.settings[0].availability_type == "REGIONAL" &&
      google_sql_database_instance.app.deletion_protection && google_sql_database_instance.app.settings[0].deletion_protection_enabled &&
      google_sql_database_instance.app.settings[0].backup_configuration[0].point_in_time_recovery_enabled &&
      google_compute_firewall.database_clients.target_service_accounts == toset([google_service_account.app.email, google_service_account.private["services"].email]) &&
      !contains(google_compute_firewall.database_clients.target_service_accounts, google_service_account.private["runtime"].email) &&
      google_compute_firewall.database_clients.priority < google_compute_firewall.database_deny.priority &&
      google_compute_firewall.database_deny.destination_ranges == google_compute_firewall.database_clients.destination_ranges &&
      length(google_compute_instance_iam_member.private_admin) == 2 * length(var.admin_members)
    )
    error_message = "Keep service VMs and HA Cloud SQL private, supply Cloud NAT, require verified TLS/PITR, exclude runtime database access and grant private-host administration."
  }

  assert {
    condition = (
      !google_compute_network.app.auto_create_subnetworks && google_compute_network.app.delete_default_routes_on_create &&
      google_compute_subnetwork.app.network == google_compute_network.app.id &&
      google_compute_subnetwork.app.ip_cidr_range == var.subnet_cidr && google_compute_subnetwork.app.private_ip_google_access &&
      google_compute_route.internet.network == google_compute_network.app.id &&
      google_compute_route.internet.dest_range == "0.0.0.0/0" && google_compute_route.internet.next_hop_gateway == "default-internet-gateway" &&
      google_compute_instance.app.network_interface[0].subnetwork == google_compute_subnetwork.app.id &&
      google_compute_instance.app.network_interface[0].access_config[0].nat_ip == google_compute_address.app.address
    )
    error_message = "Use the custom subnet, explicit internet route and reserved IP; do not rely on the default network."
  }

  assert {
    condition = (
      google_compute_firewall.web.network == google_compute_network.app.id &&
      google_compute_firewall.web.direction == "INGRESS" && google_compute_firewall.web.source_ranges == toset(["0.0.0.0/0"]) &&
      one(google_compute_firewall.web.allow).protocol == "tcp" && one(google_compute_firewall.web.allow).ports == tolist(["80", "443"]) &&
      google_compute_firewall.iap_ssh.network == google_compute_network.app.id &&
      google_compute_firewall.iap_ssh.direction == "INGRESS" && google_compute_firewall.iap_ssh.source_ranges == toset(["35.235.240.0/20"]) &&
      one(google_compute_firewall.iap_ssh.allow).protocol == "tcp" && one(google_compute_firewall.iap_ssh.allow).ports == tolist(["22"]) &&
      google_compute_firewall.web.target_service_accounts == toset([google_service_account.app.email]) &&
      google_compute_firewall.iap_ssh.target_service_accounts == toset(concat([google_service_account.app.email], [for account in google_service_account.private : account.email]))
    )
    error_message = "Only HTTP/HTTPS may be public. SSH must use IAP, and rules must target this VM's dedicated identity."
  }

  assert {
    condition = (
      google_compute_instance.app.metadata["enable-oslogin"] == "TRUE" &&
      google_compute_instance.app.metadata["block-project-ssh-keys"] == "TRUE" &&
      google_compute_instance.app.metadata["serial-port-enable"] == "FALSE" &&
      google_compute_instance.app.metadata["disable-legacy-endpoints"] == "TRUE" &&
      !contains(keys(google_compute_instance.app.metadata), "ssh-keys") &&
      google_compute_instance.app.service_account[0].email == google_service_account.app.email &&
      google_compute_instance.app.deletion_protection && !google_compute_instance.app.boot_disk[0].auto_delete &&
      google_compute_instance.app.shielded_instance_config[0].enable_secure_boot &&
      google_compute_instance.app.shielded_instance_config[0].enable_vtpm &&
      google_compute_instance.app.shielded_instance_config[0].enable_integrity_monitoring
    )
    error_message = "Require OS Login, Shielded VM protections, VM deletion protection and retained boot data; never install project SSH keys."
  }

  assert {
    condition = (
      alltrue([for binding in google_iap_tunnel_instance_iam_member.admin :
        binding.instance == google_compute_instance.app.name && binding.role == "roles/iap.tunnelResourceAccessor" &&
        contains(var.admin_members, binding.member) && binding.condition[0].expression == "destination.port == 22"
      ]) &&
      alltrue([for binding in google_compute_instance_iam_member.admin : binding.instance_name == google_compute_instance.app.name && binding.role == "roles/compute.osAdminLogin"]) &&
      alltrue([for binding in google_service_account_iam_member.admin : binding.service_account_id == google_service_account.app.name && binding.role == "roles/iam.serviceAccountUser"]) &&
      alltrue([for binding in google_project_iam_member.admin_discovery : binding.role == "roles/compute.viewer" && contains(var.admin_members, binding.member)]) &&
      alltrue([for api in google_project_service.required : !api.disable_on_destroy])
    )
    error_message = "Scope administrator login/tunnels to this VM, limit IAP to port 22, and retain enabled shared APIs. The app identity must receive no project roles."
  }

  assert {
    condition = (
      yamldecode(module.bootstrap.cloud_init["app"]).write_files[0].permissions == "0600" &&
      yamldecode(module.bootstrap.cloud_init["app"]).write_files[0].encoding == "b64" &&
      strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["app"]).write_files[0].content), "test-only-$pass\\'word") &&
      yamldecode(module.bootstrap.cloud_init["app"]).runcmd[1] == ["git", "clone", "--branch", var.repository_ref, "--", var.repository_url, "/opt/contract-app"] &&
      yamldecode(module.bootstrap.cloud_init["app"]).runcmd[3] == ["python3", "/opt/contract-app/deploy/terraform/start.py", "--role", "app"] &&
      !strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["runtime"]).write_files[0].content), "POSTGRES_PASSWORD=") &&
      !strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["app"]).write_files[0].content), "POSTGRES_ADMIN_PASSWORD=")
    )
    error_message = "Bootstrap must preserve literal password characters, protect environment files, provision runtime keys before Compose, and start with storage unconfigured."
  }
}

run "reject_public_administrators" {
  command = plan
  variables { admin_members = ["allUsers"] }
  expect_failures = [var.admin_members]
}

run "require_administrative_path" {
  command = plan
  variables { admin_members = [] }
  expect_failures = [var.admin_members]
}

run "reject_zone_in_another_region" {
  command = plan
  variables { zone = "europe-west1-b" }
  expect_failures = [google_compute_instance.app]
}

run "reject_repository_credentials" {
  command = plan
  variables { repository_url = "https://token@github.com/example/meshable-ops.git" }
  expect_failures = [var.repository_url]
}

run "reject_domain_with_scheme" {
  command = plan
  variables { app_domain = "https://workspace.example.com" }
  expect_failures = [var.app_domain]
}
