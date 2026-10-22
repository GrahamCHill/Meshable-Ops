mock_provider "azurerm" {
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
      identity = {
        principal_id = "12345678-1234-1234-1234-123456789012"
        tenant_id    = "12345678-1234-1234-1234-123456789012"
      }
    }
  }
}
mock_provider "random" {}

variables {
  repository_url          = "https://github.com/example/contracts.git"
  app_domain              = "contracts.example.com"
  acme_email              = "admin@example.com"
  ssh_public_key          = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKBceIFK1+s42pZpC4FAAEILfHWFgHQ0qN0omgnCiVGk terraform-mock-test"
  admin_cidr              = "10.50.0.0/24"
  jwt_secret              = "test-only"
  superset_secret_key     = "test-only"
  guest_token_jwt_secret  = "test-only"
  superset_admin_password = "test-only"
  bootstrap_users_json    = "[]"
}

run "private_vm_and_managed_storage" {
  command = apply

  assert {
    condition     = azurerm_network_interface.app.ip_configuration[0].public_ip_address_id == null
    error_message = "The VM NIC must not have a public IP."
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
