# Terraform deployments

Provision Meshable Ops on **AWS, Azure or GCP** with managed PostgreSQL and separate app, private services and private runtime hosts. Follow the [step-by-step deployment guide](../docs/terraform-deployment.md) and [environment-file guide](../docs/deployment-environment.md).

| Module | Provisioned services |
| --- | --- |
| [AWS](aws/) | Dedicated VPC, explicit public/private/isolated database routing, app EC2/Elastic IP, two private EC2 hosts, NAT, private Multi-AZ RDS PostgreSQL, scoped instance identity and private S3 buckets |
| [Azure](azure/) | VNet/subnets, three private VMs, web-only Standard load balancer, explicit outbound connectivity, NAT for private services, delegated/private-DNS PostgreSQL Flexible Server, managed-identity Blob access and optional Bastion |
| [GCP](gcp/) | Custom VPC/subnets, app VM/static IP, two private VMs, Cloud NAT, private-service-access Cloud SQL, OS Login/IAP administration and dedicated service accounts |

The app host runs Caddy, React/nginx and Kotlin. Superset/Celery/Redis run on the private services host. Python extraction, PDF rendering and isolated Python/Kotlin plugin execution run on the private runtime host. PostgreSQL/pgvector and Superset metadata use separate managed databases/accounts. GCP original-file storage is configured separately under Administration.

Services use a separate persistent data disk mounted at `/srv/meshable-data`, protected with `prevent_destroy` and reused when that VM is replaced in the same zone. Startup fails closed if the mount is missing; backups are still required.

## Start

Authenticate your cloud CLI/SDK and install Terraform **1.10+**, Python 3 and Git. From the repository root, using Bash:

```bash
cd infra/gcp # or infra/aws or infra/azure
cp terraform.tfvars.example terraform.tfvars
# Edit cloud/project, repository/ref, domain, network/admin access and sizing.
python3 ../../deploy/github/setup-terraform-secrets.py --admin-email owner@example.com
# Configure backend.tf/state.tfbackend from the supplied examples first.
terraform init -backend-config=state.tfbackend
terraform fmt -check -recursive
terraform validate
terraform plan -out=deployment.tfplan
terraform apply deployment.tfplan
```

Read the guide's provider-specific permissions, HA/region requirements, DNS and administration steps before applying. Inputs/state/bootstrap metadata contain secrets. Protect them and configure an encrypted remote state backend with locking. Defaults include billed managed database HA and private-host outbound networking.

Cloud-init writes private role settings to `/opt/contract-app/.env.production`. Run `sudo python3 deploy/terraform/start.py --role ROLE` on that host to apply its existing settings. Do not start the ordinary single-host Compose stack on these machines.

## Organization and checks

Each cloud directory is a root module. Networking, firewalls, managed databases, compute, identity and storage are split by responsibility. Shared [role environments](modules/split-stack/) and [cloud-init generation](modules/host-bootstrap/) avoid three independent bootstrap implementations. [Split Compose](../deploy/terraform/compose.split.yml) selects the services for each host.

Run `terraform test` after initialization in each cloud directory. All provider resources are mocked; tests create no real cloud resources. GitHub Actions validates AWS/Azure/GCP and checks the split Compose roles without starting them.

The old all-in-one Terraform layouts require a [planned migration](../docs/terraform-deployment.md#existing-installations). Applying updated user data/custom data can replace VMs and does not migrate Docker volumes. Managed PostgreSQL backups include migrated history; original files and wrapping keys need separate backups.
