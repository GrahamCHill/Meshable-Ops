resource "aws_security_group" "private" {
  for_each    = toset(["services", "runtime", "database"])
  name_prefix = "${var.name}-${each.key}-"
  description = "Private ${each.key} tier; explicit source security groups only"
  vpc_id      = aws_vpc.app.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
locals {
  private_ingress = {
    history       = { target = "services", source = "app", port = 27017 }
    analytics     = { target = "services", source = "app", port = 8088 }
    extraction    = { target = "runtime", source = "app", port = 8000 }
    rendering     = { target = "runtime", source = "app", port = 8001 }
    plugins       = { target = "runtime", source = "app", port = 8090 }
    app_sql       = { target = "database", source = "app", port = 5432 }
    analytics_sql = { target = "database", source = "services", port = 5432 }
    services_ssh  = { target = "services", source = "app", port = 22 }
    runtime_ssh   = { target = "runtime", source = "app", port = 22 }
  }
}
resource "aws_vpc_security_group_ingress_rule" "private" {
  for_each                     = local.private_ingress
  security_group_id            = aws_security_group.private[each.value.target].id
  referenced_security_group_id = each.value.source == "app" ? aws_security_group.app.id : aws_security_group.private[each.value.source].id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
}
