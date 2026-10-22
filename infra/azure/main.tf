resource "azurerm_resource_group" "app" {
  name     = "${var.name}-rg"
  location = var.location
}
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
resource "azurerm_public_ip" "app" {
  name                = "${var.name}-ip"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  allocation_method   = "Static"
  sku                 = "Standard"
}
resource "azurerm_lb" "app" {
  # Detach the public IP from an existing NIC before reusing it on the load balancer.
  depends_on = [azurerm_network_interface.app]

  name                = "${var.name}-lb"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  sku                 = "Standard"

  frontend_ip_configuration {
    name                 = "public"
    public_ip_address_id = azurerm_public_ip.app.id
  }
}
resource "azurerm_lb_backend_address_pool" "app" {
  name            = "app"
  loadbalancer_id = azurerm_lb.app.id
}
# Probe HTTP so certificate issuance can start before HTTPS becomes available.
resource "azurerm_lb_probe" "app" {
  name            = "http"
  loadbalancer_id = azurerm_lb.app.id
  protocol        = "Tcp"
  port            = 80
}
resource "azurerm_lb_rule" "web" {
  for_each = { http = 80, https = 443 }

  name                           = each.key
  loadbalancer_id                = azurerm_lb.app.id
  protocol                       = "Tcp"
  frontend_port                  = each.value
  backend_port                   = each.value
  frontend_ip_configuration_name = "public"
  backend_address_pool_ids       = [azurerm_lb_backend_address_pool.app.id]
  probe_id                       = azurerm_lb_probe.app.id
  disable_outbound_snat          = true
}
# Explicit SNAT keeps package downloads, Git, and ACME working on the private NIC.
resource "azurerm_lb_outbound_rule" "app" {
  name                    = "outbound"
  loadbalancer_id         = azurerm_lb.app.id
  protocol                = "All"
  backend_address_pool_id = azurerm_lb_backend_address_pool.app.id

  frontend_ip_configuration {
    name = "public"
  }

  depends_on = [azurerm_lb_rule.web]
}
resource "azurerm_network_security_group" "app" {
  name                = "${var.name}-nsg"
  location            = var.location
  resource_group_name = azurerm_resource_group.app.name
  security_rule {
    name                       = "http"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
  security_rule {
    name                       = "https"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
  security_rule {
    name                       = "ssh"
    priority                   = 120
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.admin_cidr
    destination_address_prefix = "*"
  }
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
resource "azurerm_network_interface_security_group_association" "app" {
  network_interface_id      = azurerm_network_interface.app.id
  network_security_group_id = azurerm_network_security_group.app.id
}
resource "azurerm_network_interface_backend_address_pool_association" "app" {
  network_interface_id    = azurerm_network_interface.app.id
  ip_configuration_name   = "primary"
  backend_address_pool_id = azurerm_lb_backend_address_pool.app.id
}
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
resource "azurerm_linux_virtual_machine" "app" {
  # Establish outbound connectivity and the firewall before cloud-init starts.
  depends_on = [
    azurerm_network_interface_security_group_association.app,
    azurerm_network_interface_backend_address_pool_association.app,
    azurerm_lb_outbound_rule.app,
  ]

  name                  = "${var.name}-vm"
  resource_group_name   = azurerm_resource_group.app.name
  location              = var.location
  size                  = var.vm_size
  admin_username        = "appadmin"
  network_interface_ids = [azurerm_network_interface.app.id]
  admin_ssh_key {
    username   = "appadmin"
    public_key = var.ssh_public_key
  }
  identity {
    type = "SystemAssigned"
  }
  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = 80
  }
  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
  custom_data = base64encode(templatefile("${path.module}/cloud-init.tftpl", {
    repository_url          = var.repository_url
    repository_ref          = var.repository_ref
    app_domain              = var.app_domain
    acme_email              = var.acme_email
    jwt_secret              = var.jwt_secret
    superset_secret_key     = var.superset_secret_key
    guest_token_jwt_secret  = var.guest_token_jwt_secret
    superset_admin_password = var.superset_admin_password
    bootstrap_users_json    = var.bootstrap_users_json
    storage_account         = azurerm_storage_account.documents.name
    storage_container       = azurerm_storage_container.documents.name
    storage_root_path       = var.storage_root_path
  }))
}
resource "azurerm_role_assignment" "documents" {
  scope                = azurerm_storage_account.documents.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_virtual_machine.app.identity[0].principal_id
}
