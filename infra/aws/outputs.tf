output "public_ip" {
  value = aws_eip.app.public_ip
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
