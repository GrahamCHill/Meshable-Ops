resource "azurerm_resource_group" "app" {
  name     = "${var.name}-rg"
  location = var.location
}
