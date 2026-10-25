mock_provider "azurerm" {
  mock_resource "azurerm_managed_disk" {
    defaults = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Compute/disks/services-data" }
  }
  mock_resource "azurerm_virtual_network" {
    defaults = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/virtualNetworks/app" }
  }
  mock_resource "azurerm_private_dns_zone" {
    defaults = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/privateDnsZones/test.postgres.database.azure.com" }
  }
  mock_resource "azurerm_postgresql_flexible_server" {
    defaults = {
      id   = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.DBforPostgreSQL/flexibleServers/test-postgres"
      fqdn = "test-postgres.postgres.database.azure.com"
    }
  }
  mock_resource "azurerm_nat_gateway" {
    defaults = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/natGateways/private" }
  }
  mock_resource "azurerm_subnet" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/virtualNetworks/test/subnets/app"
    }
  }
  mock_resource "azurerm_public_ip" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/publicIPAddresses/app"
    }
  }
  mock_resource "azurerm_lb" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/loadBalancers/app"
    }
  }
  mock_resource "azurerm_lb_backend_address_pool" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/loadBalancers/app/backendAddressPools/app"
    }
  }
  mock_resource "azurerm_lb_probe" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/loadBalancers/app/probes/http"
    }
  }
  mock_resource "azurerm_network_interface" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/networkInterfaces/app"
    }
  }
  mock_resource "azurerm_network_security_group" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/networkSecurityGroups/app"
    }
  }
  mock_resource "azurerm_storage_account" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Storage/storageAccounts/documents"
    }
  }
  mock_resource "azurerm_linux_virtual_machine" {
    defaults = {
      id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Compute/virtualMachines/app"
      identity = {
        principal_id = "12345678-1234-1234-1234-123456789012"
        tenant_id    = "12345678-1234-1234-1234-123456789012"
      }
    }
  }
}
mock_provider "random" {}

override_resource {
  target = azurerm_linux_virtual_machine.private["services"]
  values = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Compute/virtualMachines/services" }
}
override_resource {
  target = azurerm_linux_virtual_machine.private["runtime"]
  values = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Compute/virtualMachines/runtime" }
}

variables {
  postgres_admin_password    = "Test-only-managed-postgres-password-1234!"
  postgres_password          = "test-only-postgres_password-long-random-value"
  superset_database_password = "test-only-superset_database_password-long-random-value"
  secrets_encryption_key     = "dGVzdC1vbmx5LXdyYXBwaW5nLWtleS0zMi1ieXRlcyEh"
  document_render_token      = "test-only-document_render_token-long-random-value"
  plugin_runner_token        = "test-only-plugin_runner_token-long-random-value"
  repository_url             = "https://github.com/example/contracts.git"
  app_domain                 = "contracts.example.com"
  acme_email                 = "admin@example.com"
  ssh_public_key             = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKBceIFK1+s42pZpC4FAAEILfHWFgHQ0qN0omgnCiVGk terraform-mock-test"
  admin_cidr                 = "10.50.0.0/24"
  jwt_secret                 = "test-only"
  superset_secret_key        = "test-only"
  guest_token_jwt_secret     = "test-only"
  superset_admin_password    = "test-only"
  bootstrap_users_json       = "[{\"email\":\"owner@example.com\",\"password\":\"test-only-password-1234\",\"role\":\"SUPER_ADMIN\"}]"
}

run "private_vm_and_managed_storage" {
  command = apply

  assert {
    condition = (
      alltrue([for nic in azurerm_network_interface.private : nic.ip_configuration[0].public_ip_address_id == null]) &&
      azurerm_managed_disk.services.disk_size_gb == 128 && azurerm_virtual_machine_data_disk_attachment.services.lun == 10 &&
      azurerm_virtual_machine_data_disk_attachment.services.managed_disk_id == azurerm_managed_disk.services.id &&
      azurerm_virtual_machine_data_disk_attachment.services.virtual_machine_id == azurerm_linux_virtual_machine.private["services"].id &&
      alltrue([for subnet in azurerm_subnet.private : !subnet.default_outbound_access_enabled]) &&
      alltrue([for association in azurerm_subnet_nat_gateway_association.private : association.nat_gateway_id == azurerm_nat_gateway.private.id]) &&
      alltrue([for nsg in azurerm_network_security_group.private : one([for rule in nsg.security_rule : rule if rule.name == "from-app"]).source_address_prefix == "${local.private_ips.app}/32" && one([for rule in nsg.security_rule : rule if rule.name == "deny-other-inbound"]).access == "Deny"]) &&
      !azurerm_postgresql_flexible_server.app.public_network_access_enabled &&
      azurerm_postgresql_flexible_server.app.delegated_subnet_id == azurerm_subnet.database.id &&
      azurerm_postgresql_flexible_server.app.private_dns_zone_id == azurerm_private_dns_zone.database.id &&
      azurerm_private_dns_zone_virtual_network_link.database.virtual_network_id == azurerm_virtual_network.app.id &&
      azurerm_postgresql_flexible_server.app.backup_retention_days == 14 &&
      azurerm_postgresql_flexible_server.app.high_availability[0].mode == "ZoneRedundant" &&
      azurerm_postgresql_flexible_server_configuration.app["require_secure_transport"].value == "on" &&
      azurerm_postgresql_flexible_server_configuration.app["azure.extensions"].value == "VECTOR,UUID-OSSP" &&
      azurerm_management_lock.database.lock_level == "CanNotDelete" &&
      !strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["runtime"]).write_files[0].content), "POSTGRES_PASSWORD=")
    )
    error_message = "Private roles need explicit NAT/firewalls and a private-DNS, delegated, HA managed PostgreSQL server with TLS and pgvector support."
  }

  assert {
    condition     = azurerm_network_interface.app.ip_configuration[0].public_ip_address_id == null
    error_message = "The VM NIC must not have a public IP."
  }

  assert {
    condition = (
      !azurerm_subnet.app.default_outbound_access_enabled &&
      azurerm_network_interface.app.ip_configuration[0].subnet_id == azurerm_subnet.app.id &&
      azurerm_network_interface_security_group_association.app.network_interface_id == azurerm_network_interface.app.id &&
      azurerm_network_interface_security_group_association.app.network_security_group_id == azurerm_network_security_group.app.id &&
      azurerm_linux_virtual_machine.app.disable_password_authentication && length(azurerm_bastion_host.app) == 0
    )
    error_message = "Use the explicit outbound path, attach the firewall to the private NIC, and require SSH keys. Bastion must remain opt-in."
  }

  assert {
    condition = (
      one([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "ssh"]).source_address_prefix == var.admin_cidr &&
      one([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "load-balancer-probe"]).source_address_prefix == "AzureLoadBalancer" &&
      one([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "deny-other-inbound"]).priority == 200 &&
      one([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "deny-other-inbound"]).access == "Deny" &&
      alltrue([for rule in azurerm_network_security_group.app.security_rule : contains(["80", "443", "22"], rule.destination_port_range) || rule.access == "Deny" || (rule.destination_port_range == "8080" && rule.source_address_prefix == "${local.private_ips.runtime}/32")])
    )
    error_message = "Allow only web, trusted private SSH and health probes, and override Azure's broad default VNet ingress."
  }

  assert {
    condition = (
      azurerm_lb.app.frontend_ip_configuration[0].public_ip_address_id == azurerm_public_ip.app.id &&
      toset([for rule in azurerm_lb_rule.web : rule.frontend_port]) == toset([80, 443]) &&
      alltrue([for rule in azurerm_lb_rule.web : rule.protocol == "Tcp" && rule.backend_port == rule.frontend_port &&
        rule.backend_address_pool_ids == tolist([azurerm_lb_backend_address_pool.app.id]) &&
        rule.probe_id == azurerm_lb_probe.app.id && rule.disable_outbound_snat
      ])
    )
    error_message = "Only HTTP/HTTPS must be forwarded through the public load balancer to the application pool."
  }

  assert {
    condition = (
      azurerm_lb_probe.app.protocol == "Tcp" && azurerm_lb_probe.app.port == 80 &&
      azurerm_lb_outbound_rule.app.protocol == "All" &&
      azurerm_lb_outbound_rule.app.backend_address_pool_id == azurerm_lb_backend_address_pool.app.id &&
      azurerm_network_interface_backend_address_pool_association.app.network_interface_id == azurerm_network_interface.app.id &&
      azurerm_network_interface_backend_address_pool_association.app.backend_address_pool_id == azurerm_lb_backend_address_pool.app.id
    )
    error_message = "The private NIC must join the pool with outbound Internet access and a probe that works before TLS issuance."
  }

  assert {
    condition = (
      azurerm_storage_account.documents.identity[0].type == "SystemAssigned" &&
      azurerm_linux_virtual_machine.app.identity[0].type == "SystemAssigned" &&
      azurerm_role_assignment.documents.principal_id == azurerm_linux_virtual_machine.app.identity[0].principal_id &&
      azurerm_role_assignment.documents.scope == azurerm_storage_account.documents.id &&
      azurerm_role_assignment.documents.role_definition_name == "Storage Blob Data Contributor"
    )
    error_message = "Storage and VM must have managed identities, with the VM identity authorized for document storage."
  }

  assert {
    condition = (
      azurerm_storage_account.documents.https_traffic_only_enabled &&
      azurerm_storage_account.documents.min_tls_version == "TLS1_2" &&
      !azurerm_storage_account.documents.allow_nested_items_to_be_public &&
      azurerm_storage_container.documents.container_access_type == "private"
    )
    error_message = "Document storage must stay private and enforce HTTPS with TLS 1.2."
  }
}

run "bastion_without_existing_vpn" {
  command = apply
  variables {
    enable_bastion = true
    admin_cidr     = null
    vnet_cidr      = "10.48.0.0/20"
  }
  override_resource {
    target = azurerm_subnet.bastion[0]
    values = { id = "/subscriptions/12345678-1234-1234-1234-123456789012/resourceGroups/test/providers/Microsoft.Network/virtualNetworks/test/subnets/AzureBastionSubnet" }
  }
  assert {
    condition = (
      azurerm_bastion_host.app[0].sku == "Standard" && azurerm_bastion_host.app[0].tunneling_enabled &&
      azurerm_subnet.bastion[0].name == "AzureBastionSubnet" &&
      azurerm_subnet.bastion[0].address_prefixes == tolist(["10.48.0.128/26"]) &&
      azurerm_subnet.app.address_prefixes == tolist(["10.48.0.16/28"]) &&
      azurerm_bastion_host.app[0].ip_configuration[0].subnet_id == azurerm_subnet.bastion[0].id &&
      azurerm_bastion_host.app[0].ip_configuration[0].public_ip_address_id == azurerm_public_ip.bastion[0].id &&
      azurerm_public_ip.bastion[0].sku == "Standard" && azurerm_public_ip.bastion[0].allocation_method == "Static" &&
      one([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "bastion-ssh"]).source_address_prefix == azurerm_subnet.bastion[0].address_prefixes[0] &&
      length([for rule in azurerm_network_security_group.app.security_rule : rule if rule.name == "ssh"]) == 0
    )
    error_message = "Optional Bastion needs its own /26 subnet/static IP and an SSH rule limited to that subnet; no VPN/public SSH rule is needed."
  }
}

run "require_administrative_path" {
  command = plan
  variables {
    enable_bastion = false
    admin_cidr     = null
  }
  expect_failures = [azurerm_linux_virtual_machine.app]
}

run "reject_worldwide_ssh" {
  command = plan
  variables { admin_cidr = "0.0.0.0/0" }
  expect_failures = [var.admin_cidr]
}

run "reject_incompatible_database_password" {
  command = plan
  variables { postgres_admin_password = "lowercaseonlypasswordwithoutmixedclasses" }
  expect_failures = [var.postgres_admin_password]
}
