resource "aws_ebs_volume" "services" {
  lifecycle { prevent_destroy = true }
  availability_zone = data.aws_availability_zones.available.names[0]
  type              = "gp3"
  size              = 128
  encrypted         = true
  tags              = { Name = "${var.name}-services-data" }
}
resource "aws_volume_attachment" "services" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.services.id
  instance_id = aws_instance.private["services"].id
}
