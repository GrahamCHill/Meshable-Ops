output "public_ip" {
  value = aws_eip.app.public_ip
}
output "vpc_id" {
  value = aws_vpc.app.id
}
output "subnet_id" {
  value = aws_subnet.app.id
}
output "private_ip" {
  value = aws_instance.app.private_ip
}
output "application_url" {
  value = "https://${var.app_domain}"
}
output "document_bucket" {
  value = aws_s3_bucket.documents.id
}
output "access_log_bucket" {
  value = aws_s3_bucket.access_logs.id
}
output "private_hosts" {
  value = local.private_ips
}

output "database_host" {
  value = aws_db_instance.app.address
}

output "services_data_device" {
  value = "ebs:${aws_ebs_volume.services.id}"
}

