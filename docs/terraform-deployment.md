# AWS and Azure Terraform deployment

Use Terraform when you want Meshable Ops to provision a cloud host and its network, rather than supplying an existing Linux VM. Both [root modules](../infra/README.md) create infrastructure and run cloud-init to install the same production Compose stack described in the [deployment guide](deployment.md).

These are single-host deployments. The VM runs application services, PostgreSQL/pgvector, MongoDB, Superset/Redis, and the private Python rendering/extraction services. Provider-native object storage retains original documents. This topology does not provide multi-zone availability or managed databases.

Private buckets/containers mean identity-restricted data access; these modules use the providers' authenticated public HTTPS storage endpoints. They do not create S3 VPC endpoints or Azure private storage endpoints.

## AWS

The [AWS module](../infra/aws/) provisions:

- A dedicated VPC with DNS support/hostnames, an app subnet, internet gateway, default internet route, and explicit subnet route-table association. It works in accounts without a default VPC.
- EC2 with an encrypted root disk, instance role, required IMDSv2, and a managed Elastic IP. Automatic public IP assignment stays off on both subnet and instance.
- A security group allowing public TCP 80/443 and TCP 22 only from `admin_cidr`; other inbound ports are closed. An internet route and outbound access support package/image downloads, TLS renewal, storage, and enabled providers.
- A private document bucket and separate private access-log bucket, encrypted storage, public-access blocks, and policies rejecting insecure transport. The application role is scoped to the document prefix, without access to the log bucket.

`vpc_cidr` defaults to `10.42.0.0/16`; the app subnet is derived automatically (`10.42.1.0/24` by default). Choose a non-overlapping IPv4 range with a /16 through /20 prefix when connecting other networks. `admin_cidr` should normally be your trusted public IP with `/32`; `0.0.0.0/0` is rejected. The route and subnet association exist before instance creation. Cloud-init waits for internet reachability while Terraform associates the Elastic IP.

Routing follows [AWS's internet-gateway model](https://docs.aws.amazon.com/vpc/latest/userguide/route-table-options.html#route-tables-internet-gateway). The VM is in a public routed subnet, protected by its security group; its internal Compose services have no public host ports. This example does not create a NAT gateway, private app tier, VPN, or load balancer.

## Azure

The [Azure module](../infra/azure/) provisions:

- A dedicated VNet, derived app subnet, and private VM NIC with an associated network security group. The VM has no public IP and uses SSH keys.
- A Standard Load Balancer with a static public IP and only TCP 80/443 forwarding to Caddy. The TCP 80 health probe works before certificate issuance. Public SSH has no forwarding rule.
- An explicit load-balancer outbound SNAT rule, backend-pool membership, and subnet default outbound access disabled. These dependencies are established before the VM boots.
- NSG rules for web traffic, explicitly permitted private SSH, and the `AzureLoadBalancer` health probe, followed by an explicit deny overriding Azure's default broad VNet inbound allowance. Database, backend, renderer, and analytics service ports remain closed to other VNet hosts.
- A private Blob container with HTTPS/TLS 1.2 enforcement, VM managed identity, and scoped storage authorization.

`vnet_cidr` defaults to `10.40.0.0/16`, with app subnet `10.40.1.0/24`. A custom /16 through /20 IPv4 range derives its contained subnets automatically. Azure supplies its default VNet DNS. The ingress/probe and explicit outbound paths follow [Azure load-balancer guidance](https://learn.microsoft.com/en-us/azure/load-balancer/load-balancer-best-practices).

Choose an administration path before applying:

| Mode | Inputs and result |
| --- | --- |
| Provision Bastion | Set `enable_bastion = true`. Terraform creates billed Standard Azure Bastion, a dedicated `AzureBastionSubnet` (/26), its own static public IP, native-client tunneling, and an app SSH rule limited to that subnet. No existing VPN is needed. |
| Existing private access | Leave Bastion off and supply `admin_cidr` for a source already connected to the VNet through VPN/peering or equivalent private connectivity. The module does not create that VPN/peering. |

The copied Azure example enables Bastion, while the module default stays off. Omitting both administration options fails validation. Broad `0.0.0.0/0` SSH access is rejected. Bastion's network requirements and native-client permissions are described in [configuration settings](https://learn.microsoft.com/en-us/azure/bastion/configuration-settings) and [native SSH access](https://learn.microsoft.com/en-us/azure/bastion/connect-vm-native-client-linux).

## Configure and deploy

Install Terraform 1.7+ and authenticate the selected provider using your normal cloud credentials. AWS can use an authenticated CLI/SSO profile; Azure can use `az login` plus the intended `ARM_SUBSCRIPTION_ID`. Production automation can use an appropriately scoped workload identity. Do not place cloud access keys in the repository.

From the repository root, using Bash or a cloud shell:

```bash
cd infra/aws # or infra/azure
cp terraform.tfvars.example terraform.tfvars
```

Edit the copied file before applying. Supply your repository URL/ref, hostname, ACME email, SSH public key, secrets, and initial `SUPER_ADMIN` account. Replace every example credential. Set the cloud region/size if needed, and choose the network/admin inputs described above. Your repository/ref must contain this version of the deployment files; cloud-init clones that revision. Private repositories require a separately configured checkout method; no Git credential is provisioned by these templates.

```bash
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out=deployment.tfplan
terraform apply deployment.tfplan
terraform output
```

Review the saved plan for replacements, access rules, and billed resources before applying. Terraform creates a VM and starts cloud-init; completion of `apply` does not confirm application health. State, saved plans, cloud-init data, and the protected VM environment contain secrets even when Terraform marks variables sensitive. The repository ignores local tfvars/state/plans; use an encrypted, access-controlled remote backend with locking for shared deployments and retain recovery copies. Terraform does not provision a secret manager or move bootstrap credentials into one.

## DNS, HTTPS, and verification

Point the configured hostname's A record at `terraform output -raw public_ip`. Domain registration and DNS-zone ownership stay with you; the modules do not modify external DNS. Remove an unrelated AAAA record unless you have configured a working IPv6 path. Public HTTP is used for certificate issuance/redirects; Caddy serves the application over HTTPS once DNS and ports 80/443 reach it.

For AWS, connect with your configured SSH key to `ubuntu@PUBLIC_IP` from the allowed administrator source. For Azure with Bastion, use the portal or run from the initialized Azure module directory:

```bash
az network bastion ssh \
  --name "$(terraform output -raw bastion_name)" \
  --resource-group "$(terraform output -raw resource_group)" \
  --target-resource-id "$(terraform output -raw vm_id)" \
  --auth-type ssh-key --username appadmin \
  --ssh-key ~/.ssh/id_ed25519
```

Use your matching private-key path and Azure permissions. When using existing private connectivity, connect directly to the emitted `private_ip` instead.

On the VM:

```bash
sudo cloud-init status --wait
cd /opt/contract-app
sudo docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml ps
```

From your client:

```bash
curl --fail https://YOUR_HOSTNAME/api/health
```

Sign in with your chosen bootstrap account, set up MFA, and configure storage folders, plugins, email, optional AI, and job schedules. The seed connection uses the EC2/managed identity without stored storage keys. A host helper generates missing database credentials, the persistent secret-wrapping key, and the renderer token; preserve `/opt/contract-app/.env.production` securely. Nothing should expose database, Redis, API, Superset, or Python service ports directly. Only Caddy's HTTP/HTTPS listener is public.

## Updates and GitHub Actions

Infrastructure provisioning is an explicit Terraform operation; CI never applies it. GitHub Actions runs formatting, validation, and mocked network/storage tests for both modules without cloud credentials. Run the same checks locally with `terraform test` after initialization.

Cloud-init bootstraps a fresh VM; changing `repository_ref` is not a reliable in-place application upgrade mechanism. For source updates use the [existing-VM deployment process](github-deployment.md) or a reviewed manual Compose update. The bundled Actions job requires SSH connectivity from its runner. Azure's load-balancer public IP is a web endpoint, not an SSH host: set the repository variable `DEPLOY_RUNNER` to a dedicated self-hosted Linux runner label with actual private connectivity, or separately adapt the deployment job to a verified Bastion tunnel. For AWS, the runner's fixed outbound IP must be included by `admin_cidr`. Do not expose SSH to the whole internet to make a hosted runner work. The Terraform cloud-init layout and Actions' release-directory layout differ; back up and deliberately migrate to the Actions layout rather than mixing directories/volumes.

## Existing installations

Back up database volumes, original objects, and the secret-wrapping key before applying network changes. Review every plan; keeping a Terraform resource address does not guarantee that the underlying VM will remain.

- **AWS:** earlier modules selected the default VPC/subnet. Switching to this module creates a dedicated network and replaces the VM because its subnet changes. Terraform does not migrate local Docker volumes. Snapshot/export and restore business data deliberately, or keep the existing installation on its former infrastructure until a planned migration.
- **Azure:** existing default VNet/app subnet ranges remain the same. Changing ranges can replace subnets/NICs/VMs. Moving an older NIC public IP onto the load balancer can interrupt connectivity; establish Bastion/private administration first. Bastion adds separate billed resources when enabled. Disabling implicit outbound access changes the subnet default; the explicit load-balancer outbound rule supplies VM connectivity.

The object-storage and identity resources retain their existing addresses. Review any deletion/replacement involving data resources. Terraform state is infrastructure metadata, not an application backup. Follow the [backup and upgrade guide](deployment.md#upgrading-an-existing-installation) for recovery requirements.
