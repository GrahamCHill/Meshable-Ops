resource "azurerm_linux_virtual_machine" "app" {
  # Establish outbound connectivity and the firewall before cloud-init starts.
  depends_on = [
    azurerm_network_interface_security_group_association.app,
    azurerm_network_interface_backend_address_pool_association.app,
    azurerm_lb_outbound_rule.app,
  ]

  name                            = "${var.name}-vm"
  resource_group_name             = azurerm_resource_group.app.name
  location                        = var.location
  size                            = var.vm_size
  admin_username                  = "appadmin"
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.app.id]
  admin_ssh_key {
    username   = "appadmin"
    public_key = var.ssh_public_key
  }
  identity {
    type = "SystemAssigned"
  }
  lifecycle {
    precondition {
      condition     = var.enable_bastion || var.admin_cidr != null
      error_message = "Enable Bastion or supply admin_cidr backed by existing private connectivity. The application load balancer does not expose SSH."
    }
  }
  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = 80
  }
  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
  custom_data = base64encode(templatefile("${path.module}/cloud-init.tftpl", {
    repository_url          = var.repository_url
    repository_ref          = var.repository_ref
    app_domain              = var.app_domain
    acme_email              = var.acme_email
    jwt_secret              = var.jwt_secret
    superset_secret_key     = var.superset_secret_key
    guest_token_jwt_secret  = var.guest_token_jwt_secret
    superset_admin_password = var.superset_admin_password
    bootstrap_users_json    = replace(jsonencode(jsondecode(var.bootstrap_users_json)), "'", "\\'")
    storage_account         = azurerm_storage_account.documents.name
    storage_container       = azurerm_storage_container.documents.name
    storage_root_path       = var.storage_root_path
  }))
}
