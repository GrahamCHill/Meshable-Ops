resource "azurerm_virtual_network" "app" {
  name                = "${var.name}-vnet"
  address_space       = ["10.40.0.0/16"]
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
}

resource "azurerm_subnet" "app" {
  name                 = "app"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = ["10.40.1.0/24"]
}

resource "azurerm_network_interface" "app" {
  name                = "${var.name}-nic"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.app.id
    private_ip_address_allocation = "Dynamic"
  }
}
