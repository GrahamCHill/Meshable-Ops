variable "service_instance_type" {
  type    = string
  default = "t3.large"
}
variable "database_instance_class" {
  type    = string
  default = "db.t4g.medium"
}
variable "database_high_availability" {
  type    = bool
  default = true
}
resource "aws_db_subnet_group" "app" {
  name_prefix = "${var.name}-"
  subnet_ids  = aws_subnet.database[*].id
}
resource "aws_db_parameter_group" "app" {
  name_prefix = "${var.name}-"
  family      = "postgres17"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}
resource "aws_db_instance" "app" {
  identifier                 = "${var.name}-postgres"
  engine                     = "postgres"
  engine_version             = "17"
  instance_class             = var.database_instance_class
  allocated_storage          = 100
  max_allocated_storage      = 500
  storage_type               = "gp3"
  storage_encrypted          = true
  username                   = "meshable_admin"
  password                   = var.postgres_admin_password
  db_subnet_group_name       = aws_db_subnet_group.app.name
  vpc_security_group_ids     = [aws_security_group.private["database"].id]
  parameter_group_name       = aws_db_parameter_group.app.name
  publicly_accessible        = false
  multi_az                   = var.database_high_availability
  backup_retention_period    = 14
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.name}-final"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
}
