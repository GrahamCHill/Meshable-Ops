data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "app" {
  key_name_prefix = "${var.name}-"
  public_key      = var.ssh_public_key
}

resource "aws_instance" "app" {
  # Cloud-init must see the intended subnet route, not the VPC's empty main table.
  depends_on = [aws_route.internet, aws_route_table_association.app, aws_iam_role_policy.documents]

  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.app.id
  private_ip                  = local.private_ips.app
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  key_name                    = aws_key_pair.app.key_name
  root_block_device {
    volume_type = "gp3"
    volume_size = 80
    encrypted   = true
  }
  user_data                   = module.bootstrap.cloud_init["app"]
  user_data_replace_on_change = true
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
  tags = { Name = var.name }
}

resource "aws_instance" "private" {
  for_each                    = toset(["services", "runtime"])
  depends_on                  = [aws_route.services_outbound, aws_route_table_association.services]
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.service_instance_type
  subnet_id                   = aws_subnet.services.id
  private_ip                  = local.private_ips[each.key]
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.private[each.key].id]
  key_name                    = aws_key_pair.app.key_name
  user_data                   = module.bootstrap.cloud_init[each.key]
  user_data_replace_on_change = true
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 100
    encrypted             = true
    delete_on_termination = false
  }
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }
  tags = { Name = "${var.name}-${each.key}" }
}
