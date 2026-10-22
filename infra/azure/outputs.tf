output "public_ip" {
  value = azurerm_public_ip.app.ip_address
}
output "private_ip" {
  value = azurerm_network_interface.app.private_ip_address
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
