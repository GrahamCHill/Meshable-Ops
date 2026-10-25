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
  custom_data = base64encode(module.bootstrap.cloud_init["app"])
}
