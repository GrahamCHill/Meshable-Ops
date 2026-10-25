variable "database_sku" {
  type    = string
  default = "GP_Standard_D2ds_v5"
}
variable "database_high_availability" {
  type    = bool
  default = true
}
variable "database_zone" {
  type    = string
  default = "1"
}
variable "database_standby_zone" {
  type    = string
  default = "2"
}
resource "azurerm_subnet" "database" {
  name                 = "postgres"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 8, 16)]
  delegation {
    name = "postgres"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}
resource "azurerm_private_dns_zone" "database" {
  name                = "${var.name}.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.app.name
}
resource "azurerm_private_dns_zone_virtual_network_link" "database" {
  name                = "postgres"
  private_dns_zone_id = azurerm_private_dns_zone.database.id
  virtual_network_id  = azurerm_virtual_network.app.id
}
resource "azurerm_network_security_group" "database" {
  name                = "${var.name}-database-nsg"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  security_rule {
    name                       = "postgres-only"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "5432"
    source_address_prefixes    = ["${local.private_ips.app}/32", "${local.private_ips.services}/32", azurerm_subnet.database.address_prefixes[0]]
    destination_address_prefix = "*"
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
resource "azurerm_subnet_network_security_group_association" "database" {
  subnet_id                 = azurerm_subnet.database.id
  network_security_group_id = azurerm_network_security_group.database.id
}
resource "azurerm_postgresql_flexible_server" "app" {
  depends_on                    = [azurerm_private_dns_zone_virtual_network_link.database, azurerm_subnet_network_security_group_association.database]
  name                          = "${var.name}-${random_id.suffix.hex}-postgres"
  resource_group_name           = azurerm_resource_group.app.name
  location                      = var.location
  version                       = "17"
  delegated_subnet_id           = azurerm_subnet.database.id
  private_dns_zone_id           = azurerm_private_dns_zone.database.id
  public_network_access_enabled = false
  administrator_login           = "meshable_admin"
  administrator_password        = var.postgres_admin_password
  sku_name                      = var.database_sku
  storage_mb                    = 131072
  backup_retention_days         = 14
  zone                          = var.database_zone
  dynamic "high_availability" {
    for_each = var.database_high_availability ? [1] : []
    content {
      mode                      = "ZoneRedundant"
      standby_availability_zone = var.database_standby_zone
    }
  }
}
resource "azurerm_postgresql_flexible_server_configuration" "app" {
  for_each  = { "azure.extensions" = "VECTOR,UUID-OSSP", "require_secure_transport" = "on", "ssl_min_protocol_version" = "TLSv1.2" }
  name      = each.key
  server_id = azurerm_postgresql_flexible_server.app.id
  value     = each.value
}
resource "azurerm_management_lock" "database" {
  name       = "protect-postgres"
  scope      = azurerm_postgresql_flexible_server.app.id
  lock_level = "CanNotDelete"
  notes      = "Remove deliberately only after taking and verifying a recovery backup."
}
