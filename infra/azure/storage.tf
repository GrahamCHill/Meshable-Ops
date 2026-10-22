resource "random_id" "suffix" {
  byte_length = 4
}

resource "azurerm_storage_account" "documents" {
  name                            = substr(replace("${var.name}${random_id.suffix.hex}", "-", ""), 0, 24)
  resource_group_name             = azurerm_resource_group.app.name
  location                        = var.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  https_traffic_only_enabled      = true
  allow_nested_items_to_be_public = false
  identity {
    type = "SystemAssigned"
  }
}

resource "azurerm_storage_container" "documents" {
  name                  = "documents"
  storage_account_id    = azurerm_storage_account.documents.id
  container_access_type = "private"
}
