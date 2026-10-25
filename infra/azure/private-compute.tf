variable "service_vm_size" {
  type    = string
  default = "Standard_D4s_v5"
}
resource "azurerm_linux_virtual_machine" "private" {
  for_each                        = azurerm_network_interface.private
  depends_on                      = [azurerm_subnet_nat_gateway_association.private, azurerm_nat_gateway_public_ip_association.private, azurerm_network_interface_security_group_association.private]
  name                            = "${var.name}-${each.key}"
  location                        = var.location
  resource_group_name             = azurerm_resource_group.app.name
  size                            = var.service_vm_size
  admin_username                  = "appadmin"
  disable_password_authentication = true
  network_interface_ids           = [each.value.id]
  admin_ssh_key {
    username   = "appadmin"
    public_key = var.ssh_public_key
  }
  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = 100
  }
  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
  custom_data = base64encode(module.bootstrap.cloud_init[each.key])
}
