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

## File layout

Each provider directory is a single Terraform root module. Terraform loads all of its `.tf`
files together; run commands from `aws/` or `azure/` as before. Resource addresses and settings
are unchanged by the file organization, so no Terraform state migration is needed.

| File | Contents |
| --- | --- |
| `compute.tf` | VM, image selection, SSH keys, and cloud-init configuration |
| `networking.tf` | VPC/VNet, subnets, network interfaces, and the AWS Elastic IP |
| `network-security.tf` | Ingress/egress rules and network security group associations |
| `identity.tf` | AWS instance role/profile and account lookup, or Azure storage role assignment |
| `storage.tf` | Document storage, access policies, and S3 access logging |
| `providers.tf` | Provider configuration |
| `versions.tf` | Terraform and provider version constraints |
| `variables.tf` | Module inputs |
| `outputs.tf` | Deployment outputs |
| `cloud-init.tftpl` | VM bootstrap template |
| `tests/security.tftest.hcl` | Mocked security regression checks |

Azure also has `resource-group.tf` for the resource group and `load-balancer.tf` for the
public IP, load balancer, backend pool membership, probes, forwarding rules, and outbound rule.
S3 buckets and their logging resources stay together in `storage.tf` so Sonar can resolve
both the logging source and its destination. The destination uses an `aws_iam_policy_document`
with an explicit S3 logging service principal so analyzers recognize it as a log delivery bucket.

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

## Application configuration after bootstrap

The cloud-init templates seed the default connection with the VM's storage identity and no stored storage keys. They do not currently provision `APP_SECRETS_ENCRYPTION_KEY`, AI credentials, named folder selections, or an indexing schedule.

After bootstrap:

1. Review the deployed user roster, including accounts created by first-volume PostgreSQL SQL initialization. The production Spring profile only controls its own user runner.
2. If credentials will be saved through Admin, add a persistent 32-byte base64 `APP_SECRETS_ENCRYPTION_KEY` to the server's protected runtime configuration and recreate the backend with the same Compose/env files. Preserve a separate secure copy for recovery; do not generate a different key for each restart.
3. Configure shared folders, feature controls, and optional AI through the app. Search uses a local matcher without API keys by default. Multiple named connections can use the existing server identity when it has suitable access.
4. Reindex once, then save a daily, weekly, interval, or next-time-after-change job. Jobs run in the backend, require the server to stay running, and assume one backend instance.

The examples do not replace production database credential review or recovery backups. See the [deployment and upgrade guide](../docs/deployment.md), [credential setup](../docs/ai-search-and-secrets.md), and [indexing jobs](../docs/search-and-indexing.md). No Terraform resources or MongoDB schema changes are required for these UI/job controls.

