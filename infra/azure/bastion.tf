# Optional private administration. This never forwards public SSH to the application VM.
resource "azurerm_subnet" "bastion" {
  count                = var.enable_bastion ? 1 : 0
  name                 = "AzureBastionSubnet"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 26 - tonumber(split("/", var.vnet_cidr)[1]), 2)]
}

resource "azurerm_public_ip" "bastion" {
  count               = var.enable_bastion ? 1 : 0
  name                = "${var.name}-bastion-ip"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  allocation_method   = "Static"
  sku                 = "Standard"
}

resource "azurerm_bastion_host" "app" {
  count               = var.enable_bastion ? 1 : 0
  name                = "${var.name}-bastion"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  sku                 = "Standard"
  tunneling_enabled   = true
  ip_configuration {
    name                 = "primary"
    subnet_id            = azurerm_subnet.bastion[0].id
    public_ip_address_id = azurerm_public_ip.bastion[0].id
  }
}
