resource "aws_vpc" "app" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.name}-vpc" }
}

resource "aws_subnet" "app" {
  vpc_id                  = aws_vpc.app.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 1)
  map_public_ip_on_launch = false
  availability_zone       = data.aws_availability_zones.available.names[0]
  tags                    = { Name = "${var.name}-app" }
}

data "aws_availability_zones" "available" { state = "available" }

locals {
  private_ips = {
    app      = cidrhost(aws_subnet.app.cidr_block, 10)
    services = cidrhost(aws_subnet.services.cidr_block, 10)
    runtime  = cidrhost(aws_subnet.services.cidr_block, 11)
  }
}

resource "aws_subnet" "services" {
  vpc_id                  = aws_vpc.app.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 2)
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = false
  tags                    = { Name = "${var.name}-private-services" }
}

resource "aws_subnet" "database" {
  count                   = 2
  vpc_id                  = aws_vpc.app.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 16 + count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${var.name}-database-${count.index}" }
}

resource "aws_eip" "nat" { domain = "vpc" }
resource "aws_nat_gateway" "services" {
  depends_on    = [aws_internet_gateway.app]
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.app.id
}
resource "aws_route_table" "services" { vpc_id = aws_vpc.app.id }
resource "aws_route" "services_outbound" {
  route_table_id         = aws_route_table.services.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.services.id
}
resource "aws_route_table_association" "services" {
  subnet_id      = aws_subnet.services.id
  route_table_id = aws_route_table.services.id
}
# Database subnets have only local VPC routes, with no internet/default route.
resource "aws_route_table" "database" { vpc_id = aws_vpc.app.id }
resource "aws_route_table_association" "database" {
  count          = 2
  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id
  tags   = { Name = "${var.name}-internet" }
}

resource "aws_route_table" "app" {
  vpc_id = aws_vpc.app.id
  tags   = { Name = "${var.name}-app" }
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.app.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.app.id
}

resource "aws_route_table_association" "app" {
  subnet_id      = aws_subnet.app.id
  route_table_id = aws_route_table.app.id
}

resource "aws_eip" "app" {
  depends_on = [aws_internet_gateway.app, aws_route.internet]
  domain     = "vpc"
  instance   = aws_instance.app.id
}
