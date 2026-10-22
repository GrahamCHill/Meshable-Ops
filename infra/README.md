# Terraform deployment examples

These examples provision a single Linux VM for the existing production Docker Compose stack,
public HTTPS ingress, and provider-native object storage. They are starting points rather than a
high-availability production topology: PostgreSQL, MongoDB, Redis, Superset, and the application
still run on the VM and must be backed up from their Docker volumes.

- `aws/`: EC2 + S3. The backend uses the EC2 instance role through the AWS default credential chain.
  Automatic public IPv4 assignment is disabled; the explicitly managed Elastic IP remains the
  application endpoint. Public ingress allows HTTP/HTTPS plus SSH from `admin_cidr`.
  Document access logs go to a separate private, encrypted S3 bucket under `documents/`.
  Both buckets deny non-HTTPS requests. The log destination is intentionally not logged to avoid
  recursive log delivery; the application role has no access to it.
- `azure/`: Azure Linux VM + Blob container. The backend uses the VM system-assigned managed identity;
  the storage account also has its own system-assigned identity. The VM NIC has only a private IP.
  A Standard Load Balancer owns the public IP and forwards only TCP 80/443 to Caddy. An explicit
  outbound rule supplies Internet access for cloud-init, image downloads, and certificate renewal.
  SSH requires existing private connectivity to the VNet, such as a VPN or Azure Bastion, and an
  appropriate `admin_cidr`; there is no public SSH forwarding rule.

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

For an existing Azure deployment, moving the public IP from the NIC to the load balancer can
interrupt connections. Establish private administrative access before applying the change and
review the Terraform plan for VM/NIC replacement. The load balancer adds Azure resources and cost.

Run the security regression checks without cloud credentials using `terraform test` in each
provider directory after initialization. These tests use mocked providers and do not deploy resources.

The examples accept application secrets as sensitive Terraform variables for clarity. Sensitive
values are still stored in Terraform state. Use an encrypted remote backend and replace cloud-init
secret delivery with AWS Secrets Manager/SSM or Azure Key Vault before production use.

