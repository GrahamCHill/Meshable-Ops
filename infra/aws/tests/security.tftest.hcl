mock_provider "aws" {}

override_data {
  target = data.aws_subnets.default
  values = { ids = ["subnet-0123456789abcdef0"] }
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
  repository_url          = "https://github.com/example/contracts.git"
  app_domain              = "contracts.example.com"
  acme_email              = "admin@example.com"
  ssh_public_key          = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKBceIFK1+s42pZpC4FAAEILfHWFgHQ0qN0omgnCiVGk terraform-mock-test"
  admin_cidr              = "203.0.113.10/32"
  jwt_secret              = "test-only"
  superset_secret_key     = "test-only"
  guest_token_jwt_secret  = "test-only"
  superset_admin_password = "test-only"
  bootstrap_users_json    = "[]"
}

run "secure_storage_and_explicit_networking" {
  command = apply

  assert {
    condition     = !aws_instance.app.associate_public_ip_address && aws_eip.app.instance == aws_instance.app.id
    error_message = "EC2 must disable automatic public IP allocation and use only the managed Elastic IP."
  }

  assert {
    condition = alltrue([
      for policy in [aws_s3_bucket_policy.documents.policy, aws_s3_bucket_policy.access_logs.policy] :
      anytrue([
        for statement in jsondecode(policy).Statement :
        statement.Effect == "Deny" && statement.Principal == "*" &&
        statement.Action == "s3:*" &&
        try(statement.Condition.Bool["aws:SecureTransport"], "") == "false"
      ])
    ])
    error_message = "Both S3 buckets must deny all operations over insecure transport for every principal."
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
      jsondecode(aws_s3_bucket_policy.access_logs.policy).Statement[0].Principal.Service == "logging.s3.amazonaws.com" &&
      jsondecode(aws_s3_bucket_policy.access_logs.policy).Statement[0].Action == "s3:PutObject" &&
      jsondecode(aws_s3_bucket_policy.access_logs.policy).Statement[0].Resource == "${aws_s3_bucket.access_logs.arn}/documents/*" &&
      jsondecode(aws_s3_bucket_policy.access_logs.policy).Statement[0].Condition.ArnLike["aws:SourceArn"] == aws_s3_bucket.documents.arn &&
      jsondecode(aws_s3_bucket_policy.access_logs.policy).Statement[0].Condition.StringEquals["aws:SourceAccount"] == "123456789012"
    )
    error_message = "Log delivery must be limited to S3 logging for this document bucket and account."
  }
}
