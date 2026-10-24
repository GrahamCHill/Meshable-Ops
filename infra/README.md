# Terraform deployments

Provision Meshable Ops on AWS or Azure, including its network, Linux VM, web ingress, and object storage. Cloud-init installs the production Docker Compose stack. Use the [deployment guide](../docs/terraform-deployment.md) for credentials, DNS/TLS, administration, backups, and upgrades.

| Module | Network and services |
| --- | --- |
| [AWS](aws/) | Dedicated DNS-enabled VPC, app subnet, internet gateway, route table/association, security group, EC2, managed Elastic IP, and private S3 buckets. No default VPC is required. SSH is restricted to `admin_cidr`. |
| [Azure](azure/) | VNet, private app subnet/NIC, NSG, private Linux VM, Standard public load balancer for TCP 80/443, explicit outbound SNAT, and private Blob container. Optional Standard Bastion supplies private administration without an existing VPN. |

These are single-VM deployments. PostgreSQL/pgvector, MongoDB, Redis, Superset, and the private Python document services run in Compose; they are not publicly exposed or replaced with managed database services. Object access uses an EC2 instance role or Azure managed identity, with no stored storage keys.

## Start

Authenticate to your chosen cloud and install Terraform **1.7+**. From the repository root:

```bash
cd infra/aws # or infra/azure
cp terraform.tfvars.example terraform.tfvars
# Edit the copied inputs: repository, domain, SSH key, initial super admin, and secrets.
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out=deployment.tfplan
terraform apply deployment.tfplan
```

The Azure example opts into billed Bastion resources; the module default is off. Alternatively supply `admin_cidr` and actual existing private connectivity. Setting a CIDR alone does not provision a VPN or peering connection. Application DNS is owner-managed: point your hostname's A record at `terraform output -raw public_ip` so Caddy can obtain its certificate.

Protect Terraform state, saved plans, and the generated server `.env.production`; all can contain credentials. Use an encrypted remote backend with access controls and locking for shared environments. Database/object backups and the persistent wrapping key remain necessary.

## Checks

After initialization, run `terraform test` in either module. Mocked providers check routing, ingress restrictions, outbound access, optional Bastion, identity, and storage protection without cloud credentials or resources. GitHub Actions runs these checks for both providers; cloud provisioning remains an explicit owner action.

Each directory is one root module. `networking.tf`, `network-security.tf`, `compute.tf`, `identity.tf`, and `storage.tf` separate responsibilities. Azure adds `load-balancer.tf`, `bastion.tf`, and `resource-group.tf`; `cloud-init.tftpl` bootstraps Compose. Inputs and outputs live in `variables.tf` and `outputs.tf`.

Before applying updated modules to an existing installation, read the [migration notes](../docs/terraform-deployment.md#existing-installations). Moving AWS from its former default VPC to the dedicated VPC replaces the VM; Terraform does not migrate Docker volume data.
