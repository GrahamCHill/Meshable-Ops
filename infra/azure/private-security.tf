resource "azurerm_network_security_group" "private" {
  for_each            = toset(["services", "runtime"])
  name                = "${var.name}-${each.key}-nsg"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  security_rule {
    name                       = "from-app"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = each.key == "services" ? ["8088"] : ["8000", "8001", "8090"]
    source_address_prefix      = "${local.private_ips.app}/32"
    destination_address_prefix = "*"
  }
  dynamic "security_rule" {
    for_each = var.admin_cidr == null ? [] : [var.admin_cidr]
    content {
      name                       = "private-ssh"
      priority                   = 110
      direction                  = "Inbound"
      access                     = "Allow"
      protocol                   = "Tcp"
      source_port_range          = "*"
      destination_port_range     = "22"
      source_address_prefix      = security_rule.value
      destination_address_prefix = "*"
    }
  }
  dynamic "security_rule" {
    for_each = var.enable_bastion ? [azurerm_subnet.bastion[0].address_prefixes[0]] : []
    content {
      name                       = "bastion-ssh"
      priority                   = 120
      direction                  = "Inbound"
      access                     = "Allow"
      protocol                   = "Tcp"
      source_port_range          = "*"
      destination_port_range     = "22"
      source_address_prefix      = security_rule.value
      destination_address_prefix = "*"
    }
  }
  security_rule {
    name                       = "deny-other-inbound"
    priority                   = 200
    direction                  = "Inbound"
    access                     = "Deny"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}
resource "azurerm_network_interface_security_group_association" "private" {
  for_each                  = azurerm_network_interface.private
  network_interface_id      = each.value.id
  network_security_group_id = azurerm_network_security_group.private[each.key].id
}
