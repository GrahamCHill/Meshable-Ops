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
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  subnet_id                   = data.aws_subnets.default.ids[0]
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  key_name                    = aws_key_pair.app.key_name
  root_block_device {
    volume_type = "gp3"
    volume_size = 80
    encrypted   = true
  }
  user_data = templatefile("${path.module}/cloud-init.tftpl", {
    repository_url          = var.repository_url
    repository_ref          = var.repository_ref
    app_domain              = var.app_domain
    acme_email              = var.acme_email
    jwt_secret              = var.jwt_secret
    superset_secret_key     = var.superset_secret_key
    guest_token_jwt_secret  = var.guest_token_jwt_secret
    superset_admin_password = var.superset_admin_password
    bootstrap_users_json    = replace(jsonencode(jsondecode(var.bootstrap_users_json)), "'", "\\'")
    storage_bucket          = aws_s3_bucket.documents.id
    storage_region          = var.aws_region
    storage_root_path       = var.storage_root_path
  })
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
  tags = { Name = var.name }
}
