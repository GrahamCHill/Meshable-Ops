locals {
  private_ips = {
    app      = cidrhost(cidrsubnet(var.vnet_cidr, 8, 1), 10)
    services = cidrhost(cidrsubnet(var.vnet_cidr, 8, 2), 10)
    runtime  = cidrhost(cidrsubnet(var.vnet_cidr, 8, 3), 10)
  }
}
resource "azurerm_subnet" "private" {
  for_each                        = { services = 2, runtime = 3 }
  name                            = each.key
  resource_group_name             = azurerm_resource_group.app.name
  virtual_network_name            = azurerm_virtual_network.app.name
  address_prefixes                = [cidrsubnet(var.vnet_cidr, 8, each.value)]
  default_outbound_access_enabled = false
}
resource "azurerm_public_ip" "nat" {
  name                = "${var.name}-outbound"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  allocation_method   = "Static"
  sku                 = "Standard"
}
resource "azurerm_nat_gateway" "private" {
  name                = "${var.name}-private-outbound"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  sku_name            = "Standard"
}
resource "azurerm_nat_gateway_public_ip_association" "private" {
  nat_gateway_id       = azurerm_nat_gateway.private.id
  public_ip_address_id = azurerm_public_ip.nat.id
}
resource "azurerm_subnet_nat_gateway_association" "private" {
  for_each       = azurerm_subnet.private
  subnet_id      = each.value.id
  nat_gateway_id = azurerm_nat_gateway.private.id
}
resource "azurerm_network_interface" "private" {
  for_each            = azurerm_subnet.private
  name                = "${var.name}-${each.key}-nic"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  ip_configuration {
    name                          = "primary"
    subnet_id                     = each.value.id
    private_ip_address_allocation = "Static"
    private_ip_address            = local.private_ips[each.key]
  }
}
