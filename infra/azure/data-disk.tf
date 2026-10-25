resource "azurerm_managed_disk" "services" {
  lifecycle { prevent_destroy = true }
  name                 = "${var.name}-services-data"
  location             = var.location
  resource_group_name  = azurerm_resource_group.app.name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = 128
}
resource "azurerm_virtual_machine_data_disk_attachment" "services" {
  managed_disk_id    = azurerm_managed_disk.services.id
  virtual_machine_id = azurerm_linux_virtual_machine.private["services"].id
  lun                = 10
  caching            = "ReadWrite"
}
