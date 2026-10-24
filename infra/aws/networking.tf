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
  tags                    = { Name = "${var.name}-app" }
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
