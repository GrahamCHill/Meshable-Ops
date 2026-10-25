mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["af-south-1a", "af-south-1b"] }
  }
  # Mock JSON rendering only; the assertions inspect the actual policy inputs.
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{}" }
  }
}

override_data {
  target = data.aws_caller_identity.current
  values = { account_id = "123456789012" }
}

override_resource {
  target = aws_s3_bucket.documents
  values = { id = "test-documents", arn = "arn:aws:s3:::test-documents" }
}

override_resource {
  target = aws_s3_bucket.access_logs
  values = { id = "test-access-logs", arn = "arn:aws:s3:::test-access-logs" }
}

variables {
  postgres_admin_password    = "test-only-postgres_admin_password-long-random-value"
  postgres_password          = "test-only-postgres_password-long-random-value"
  superset_database_password = "test-only-superset_database_password-long-random-value"
  mongo_password             = "test-only-mongo_password-long-random-value"
  secrets_encryption_key     = "dGVzdC1vbmx5LXdyYXBwaW5nLWtleS0zMi1ieXRlcyEh"
  document_render_token      = "test-only-document_render_token-long-random-value"
  plugin_runner_token        = "test-only-plugin_runner_token-long-random-value"
  repository_url             = "https://github.com/example/contracts.git"
  app_domain                 = "contracts.example.com"
  acme_email                 = "admin@example.com"
  ssh_public_key             = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKBceIFK1+s42pZpC4FAAEILfHWFgHQ0qN0omgnCiVGk terraform-mock-test"
  admin_cidr                 = "203.0.113.10/32"
  jwt_secret                 = "test-only"
  superset_secret_key        = "test-only"
  guest_token_jwt_secret     = "test-only"
  superset_admin_password    = "test-only"
  bootstrap_users_json       = "[{\"email\":\"owner@example.com\",\"password\":\"test-only-password-1234\",\"role\":\"SUPER_ADMIN\"}]"
}

run "secure_storage_and_explicit_networking" {
  command = apply

  assert {
    condition = (
      alltrue([for host in aws_instance.private : !host.associate_public_ip_address && host.subnet_id == aws_subnet.services.id && host.root_block_device[0].encrypted]) &&
      aws_route.services_outbound.nat_gateway_id == aws_nat_gateway.services.id &&
      aws_route_table_association.services.route_table_id == aws_route_table.services.id &&
      alltrue([for subnet in aws_subnet.database : !subnet.map_public_ip_on_launch]) &&
      length(toset([for subnet in aws_subnet.database : subnet.availability_zone])) == 2 &&
      aws_db_instance.app.db_subnet_group_name == aws_db_subnet_group.app.name &&
      !aws_db_instance.app.publicly_accessible && aws_db_instance.app.storage_encrypted && aws_db_instance.app.multi_az &&
      aws_db_instance.app.deletion_protection && aws_db_instance.app.backup_retention_period == 14 &&
      !aws_db_instance.app.skip_final_snapshot && one(aws_db_parameter_group.app.parameter).value == "1" &&
      aws_vpc_security_group_ingress_rule.private["app_sql"].referenced_security_group_id == aws_security_group.app.id &&
      aws_vpc_security_group_ingress_rule.private["analytics_sql"].referenced_security_group_id == aws_security_group.private["services"].id &&
      length([for rule in aws_vpc_security_group_ingress_rule.private : rule if rule.security_group_id == aws_security_group.private["database"].id]) == 2 &&
      !strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["runtime"]).write_files[0].content), "POSTGRES_PASSWORD=") &&
      !strcontains(base64decode(yamldecode(module.bootstrap.cloud_init["app"]).write_files[0].content), "POSTGRES_ADMIN_PASSWORD=")
    )
    error_message = "Keep service hosts private behind NAT, isolate the managed HA database, enforce encrypted backups/TLS, and keep database secrets off runtime hosts."
  }

  assert {
    condition = (
      aws_vpc.app.enable_dns_support && aws_vpc.app.enable_dns_hostnames &&
      aws_subnet.app.vpc_id == aws_vpc.app.id && !aws_subnet.app.map_public_ip_on_launch &&
      aws_instance.app.subnet_id == aws_subnet.app.id && aws_security_group.app.vpc_id == aws_vpc.app.id &&
      aws_internet_gateway.app.vpc_id == aws_vpc.app.id && aws_route_table.app.vpc_id == aws_vpc.app.id &&
      aws_route.internet.route_table_id == aws_route_table.app.id && aws_route.internet.destination_cidr_block == "0.0.0.0/0" &&
      aws_route.internet.gateway_id == aws_internet_gateway.app.id &&
      aws_route_table_association.app.subnet_id == aws_subnet.app.id &&
      aws_route_table_association.app.route_table_id == aws_route_table.app.id
    )
    error_message = "Create a dedicated DNS-enabled VPC, explicitly routed subnet, gateway, and matching security group; never rely on a default VPC."
  }

  assert {
    condition = (
      length(aws_security_group.app.ingress) == 4 && alltrue([
        for rule in aws_security_group.app.ingress : rule.protocol == "tcp" && rule.from_port == rule.to_port &&
        (contains([80, 443], rule.from_port) ? rule.cidr_blocks == tolist(["0.0.0.0/0"]) : rule.from_port == 22 ? rule.cidr_blocks == tolist([var.admin_cidr]) : rule.from_port == 8080 && rule.security_groups == toset([aws_security_group.private["runtime"].id]) && try(length(rule.cidr_blocks), 0) == 0)
      ])
    )
    error_message = "Only web ports may be public; SSH must be limited to the configured trusted CIDR."
  }

  assert {
    condition     = !aws_instance.app.associate_public_ip_address && aws_eip.app.instance == aws_instance.app.id
    error_message = "EC2 must disable automatic public IP allocation and use only the managed Elastic IP."
  }

  assert {
    condition = anytrue([
      for statement in jsondecode(aws_s3_bucket_policy.documents.policy).Statement :
      statement.Effect == "Deny" && statement.Principal == "*" &&
      statement.Action == "s3:*" &&
      try(statement.Condition.Bool["aws:SecureTransport"], "") == "false"
    ])
    error_message = "The document bucket must deny all operations over insecure transport for every principal."
  }

  assert {
    condition = (
      aws_s3_bucket_policy.access_logs.policy == data.aws_iam_policy_document.access_logs.json &&
      data.aws_iam_policy_document.access_logs.statement[1].effect == "Deny" &&
      data.aws_iam_policy_document.access_logs.statement[1].actions == toset(["s3:*"]) &&
      one(data.aws_iam_policy_document.access_logs.statement[1].principals).type == "*" &&
      one(data.aws_iam_policy_document.access_logs.statement[1].principals).identifiers == toset(["*"]) &&
      one(data.aws_iam_policy_document.access_logs.statement[1].condition).test == "Bool" &&
      one(data.aws_iam_policy_document.access_logs.statement[1].condition).variable == "aws:SecureTransport" &&
      one(data.aws_iam_policy_document.access_logs.statement[1].condition).values == tolist(["false"]) &&
      data.aws_iam_policy_document.access_logs.statement[1].resources == toset([
        aws_s3_bucket.access_logs.arn, "${aws_s3_bucket.access_logs.arn}/*"
      ])
    )
    error_message = "The log bucket policy must deny insecure transport for the bucket and every object."
  }

  assert {
    condition = toset(jsondecode(aws_s3_bucket_policy.documents.policy).Statement[0].Resource) == toset([
      aws_s3_bucket.documents.arn, "${aws_s3_bucket.documents.arn}/*"
    ])
    error_message = "HTTPS enforcement must cover the document bucket and all its objects."
  }

  assert {
    condition = (
      aws_s3_bucket_logging.documents.target_bucket != aws_s3_bucket_logging.documents.bucket &&
      aws_s3_bucket_logging.documents.target_bucket == aws_s3_bucket.access_logs.id &&
      aws_s3_bucket_logging.documents.target_prefix == "documents/"
    )
    error_message = "Document access logs must go to the separate log bucket under the delivery policy's prefix."
  }

  assert {
    condition = alltrue([
      for block in [aws_s3_bucket_public_access_block.documents, aws_s3_bucket_public_access_block.access_logs] :
      block.block_public_acls && block.block_public_policy && block.ignore_public_acls && block.restrict_public_buckets
    ])
    error_message = "Documents and their access logs must both block public access."
  }

  assert {
    condition = (
      data.aws_iam_policy_document.access_logs.statement[0].effect == "Allow" &&
      one(data.aws_iam_policy_document.access_logs.statement[0].principals).type == "Service" &&
      one(data.aws_iam_policy_document.access_logs.statement[0].principals).identifiers == toset(["logging.s3.amazonaws.com"]) &&
      data.aws_iam_policy_document.access_logs.statement[0].actions == toset(["s3:PutObject"]) &&
      data.aws_iam_policy_document.access_logs.statement[0].resources == toset(["${aws_s3_bucket.access_logs.arn}/documents/*"]) &&
      { for condition in data.aws_iam_policy_document.access_logs.statement[0].condition : condition.test => {
        (condition.variable) = one(condition.values)
        } } == {
        ArnLike      = { "aws:SourceArn" = aws_s3_bucket.documents.arn }
        StringEquals = { "aws:SourceAccount" = "123456789012" }
      }
    )
    error_message = "Log delivery must be limited to S3 logging for this document bucket and account."
  }
}

run "reject_worldwide_ssh" {
  command = plan
  variables { admin_cidr = "0.0.0.0/0" }
  expect_failures = [var.admin_cidr]
}

run "custom_network_range" {
  command = apply
  variables { vpc_cidr = "10.48.0.0/20" }
  assert {
    condition     = aws_vpc.app.cidr_block == "10.48.0.0/20" && aws_subnet.app.cidr_block == "10.48.0.16/28"
    error_message = "Custom VPC ranges must derive a valid, contained app subnet."
  }
}
