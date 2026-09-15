# Terraform deployment examples

These examples provision a single Linux VM for the existing production Docker Compose stack,
public HTTPS ingress, and provider-native object storage. They are starting points rather than a
high-availability production topology: PostgreSQL, MongoDB, Redis, Superset, and the application
still run on the VM and must be backed up from their Docker volumes.

- `aws/`: EC2 + S3. The backend uses the EC2 instance role through the AWS default credential chain.
- `azure/`: Azure Linux VM + Blob container. The backend uses the VM system-assigned managed identity.

Both examples clone a configurable Git repository/ref and run:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

## Usage

```bash
cd infra/aws # or infra/azure
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

Point the configured DNS hostname at the emitted public IP. Caddy obtains the TLS certificate
after DNS resolves and ports 80/443 reach the VM.

The examples accept application secrets as sensitive Terraform variables for clarity. Sensitive
values are still stored in Terraform state. Use an encrypted remote backend and replace cloud-init
secret delivery with AWS Secrets Manager/SSM or Azure Key Vault before production use.

