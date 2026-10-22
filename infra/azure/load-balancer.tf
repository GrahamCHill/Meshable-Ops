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

resource "azurerm_network_interface_backend_address_pool_association" "app" {
  network_interface_id    = azurerm_network_interface.app.id
  ip_configuration_name   = "primary"
  backend_address_pool_id = azurerm_lb_backend_address_pool.app.id
}
