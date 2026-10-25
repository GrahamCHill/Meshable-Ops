output "public_ip" {
  value = azurerm_public_ip.app.ip_address
}
output "private_ip" {
  value = azurerm_network_interface.app.private_ip_address
}
output "resource_group" {
  value = azurerm_resource_group.app.name
}
output "virtual_network_id" {
  value = azurerm_virtual_network.app.id
}
output "vm_id" {
  value = azurerm_linux_virtual_machine.app.id
}
output "bastion_name" {
  value = var.enable_bastion ? azurerm_bastion_host.app[0].name : null
}
output "application_url" {
  value = "https://${var.app_domain}"
}
output "storage_account" {
  value = azurerm_storage_account.documents.name
}
output "storage_container" {
  value = azurerm_storage_container.documents.name
}
output "private_hosts" {
  value = local.private_ips
}

output "database_host" {
  value = azurerm_postgresql_flexible_server.app.fqdn
}

output "services_data_device" {
  value = "/dev/disk/azure/scsi1/lun10"
}

output "private_vm_ids" {
  value = { for role, vm in azurerm_linux_virtual_machine.private : role => vm.id }
}

