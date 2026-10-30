## What you will be able to do

- Explain the AWS building blocks a backend team uses: IAM, VPC, EKS, RDS, S3, ECR and Secrets Manager.
- Design a network for a Kubernetes cluster and a private database.
- Give pods AWS permissions without access keys, with least privilege.
- Estimate and control the cost of a cloud environment.
- Compare EKS with AKS, GKE and OpenShift, and with running k3s on a VPS.

## Why this matters in a bank or fintech

Nigerian fintechs mostly run on AWS, Azure or Google Cloud; many banks run OpenShift in their own data centres and use the cloud for new products, subject to the CBN's guidance and the **Nigeria Data Protection Act 2023**, which matters for where customer data may live. Whatever the platform, the same ideas appear: identities and least privilege, private networks, managed databases, object storage, and a bill that grows if nobody watches it. This topic uses AWS because it's the most common, and maps each idea to the others.

## 1. IAM: who can do what

**Identity and Access Management** controls every action in an AWS account.

- **Users** are long-lived identities with passwords or access keys. Avoid them for people (use **IAM Identity Center**, single sign-on with short-lived credentials) and avoid them entirely for machines.
- **Roles** are identities that something **assumes** for a short time: a CI pipeline (through GitHub's OIDC, topic 12.5), an EC2 node, or a **pod**.
- **Policies** are JSON documents that allow or deny actions on resources. Start from nothing and allow only what's needed: **least privilege**.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject"],
    "Resource": "arn:aws:s3:::itl-pay-statements-*/statements/*"
  }]
}
```

**Pods get AWS permissions through EKS Pod Identity** (the `eks-pod-identity-agent` add-on in the starter's Terraform): you associate an IAM role with a Kubernetes service account, and the AWS SDK in the pod picks up short-lived credentials automatically. No access keys in Secrets, ever. (IRSA, the older mechanism, works the same way through OIDC.)

## 2. VPC: the private network

A **VPC** is your own network in a region, split into **subnets** in several **availability zones** (separate data centres):

```
VPC 10.20.0.0/16 (eu-west-1)
├── public subnets  (3 zones): load balancers; route to the internet gateway
└── private subnets (3 zones): EKS nodes and RDS; out to the internet only through a NAT gateway
```

- Nothing in a private subnet can be reached from the internet directly. The database lives there, with `publicly_accessible = false`.
- **Security groups** are stateful firewalls on each resource. The starter's RDS security group accepts port 5432 **only from the EKS nodes' security group**, not from an IP range.
- A **NAT gateway** lets private resources call out (pull images, reach partner APIs). It's one of the quietly expensive items, so the learning setup uses one, not one per zone.
- Tags such as `kubernetes.io/role/elb` tell the AWS Load Balancer Controller which subnets to use.

## 3. EKS, ECR and the services around Kubernetes

**Amazon EKS** runs the Kubernetes control plane for you (multi-zone, patched, upgraded on request); you provide worker nodes, here a **managed node group** of two `t3.large` instances. **Add-ons** install the cluster's plumbing: CoreDNS, kube-proxy, the VPC CNI (pods get VPC IP addresses), the Pod Identity agent and metrics-server.

**ECR** stores your images. The starter creates one repository per service with **immutable tags** (a tag always means the same image) and scanning on push. CI pushes `itl-pay/transfers:0.3.1`; the chart's `image.registry` points at ECR.

## 4. RDS and S3

**Amazon RDS for PostgreSQL** runs the database: backups, point-in-time recovery, patching and, with Multi-AZ, a standby in another zone with automatic failover. The starter uses PostgreSQL 18 (the same major version as kind) on a small `db.t4g.micro`, and:

```hcl
resource "aws_db_instance" "postgres" {
  engine         = "postgres"
  engine_version = "18"
  instance_class = "db.t4g.micro"
  storage_encrypted = true
  manage_master_user_password = true   # RDS keeps the password in AWS Secrets Manager; it never appears in state or code
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  backup_retention_period = 7
  ...
}
```

Each service still gets **its own database and user** (topic 10.4): create them once with a migration job or a small Terraform PostgreSQL provider, and store each password in Secrets Manager for External Secrets to sync.

**Amazon S3** stores objects: statements, exports, audit archives. The starter's bucket blocks all public access, keeps versions, and moves statements to cheaper Glacier Instant Retrieval after 90 days. Kafka in the cloud is **Amazon MSK** (or MSK Serverless), or Strimzi running in the cluster; MSK isn't in the starter's Terraform because even the smallest cluster costs more than the rest of the learning setup.

## 5. What it costs, and how to stop it surprising you

Rough on-demand prices in `eu-west-1`, which change, so check the AWS pricing pages before you start:

| Item | About |
| --- | --- |
| EKS control plane | US$0.10 an hour, about US$73 a month |
| Two `t3.large` nodes (spot) | US$0.03–0.04 an hour each |
| NAT gateway | US$0.045 an hour plus data, about US$35 a month |
| RDS `db.t4g.micro` | US$0.02 an hour, about US$13 a month plus storage |
| Load balancer for the Gateway | US$0.025 an hour plus usage |

A full day costs a few dollars; a forgotten month costs well over US$150. So:

1. **Create the budget first.** The starter's Terraform creates an AWS Budget that emails you at 80% of the monthly amount and when the forecast passes 100%.
2. **Tag everything** (`default_tags` in the provider) so the bill shows what belongs to ITL Pay.
3. **Destroy the same day** (`terraform destroy`), after recording your evidence.
4. Use **spot instances** for learning nodes, and one NAT gateway.

## 6. The other platforms

| | AWS EKS | Azure AKS | Google GKE | Red Hat OpenShift |
| --- | --- | --- | --- | --- |
| Control plane | Paid per hour | Free tier, paid tier with SLA | One free zonal cluster credit; Autopilot mode manages nodes | Subscription; on-premises or managed (ROSA on AWS, ARO on Azure) |
| Pod identity | EKS Pod Identity / IRSA | Workload Identity (Entra ID) | Workload Identity | Service accounts plus cloud credentials operators |
| Common in | Fintechs, startups | Banks with Microsoft estates | Data-heavy teams | **Banks' own data centres**; strong defaults (non-root, SCCs) |

Your chart, probes and GitOps setup work on all of them; the differences are identity, networking and the console. For the capstone, a **k3s VPS** (one US$10–20 machine with k3s and Traefik or Envoy Gateway) is a perfectly good deployment target and far cheaper.

**Data residency.** AWS has a region in Cape Town (`af-south-1`) and a **Local Zone in Lagos**; Azure and Google have South African regions. Where customer data may be stored is a legal and compliance decision, not just a latency one; write it in your ADR.

## Worked example: ITL Pay on EKS for one afternoon

1. 13:00. `terraform apply` (topic 12.5) creates the VPC, EKS with two spot nodes, RDS, the S3 bucket, six ECR repositories and the budget, in about 15–20 minutes.
2. CI builds and pushes the images to ECR. External Secrets syncs the database passwords from Secrets Manager. Argo CD installs the chart with `values-aws.yaml`, filled in from `terraform output`.
3. 14:00. A transfer goes through the public URL; you record `kubectl get pods`, the Argo CD screen, the AWS console showing RDS and the bucket, and Cost Explorer for the day.
4. 15:00. `terraform destroy`. The budget email never arrives, and the afternoon cost a few dollars.

## Common mistakes

- Access keys in Kubernetes Secrets or environment variables instead of Pod Identity.
- Databases in public subnets, or security groups open to `0.0.0.0/0`.
- One administrator user shared by the team.
- No budget, no tags, and a cluster left running for a month.
- Assuming the region is a latency choice only, and ignoring data-protection rules.
- Running Kafka yourself in production without the skills to operate it.

## Practice

1. Draw ITL Pay's AWS architecture: VPC, subnets, EKS, RDS, S3, ECR, Secrets Manager, and the paths a request and a database password take.
2. Write the IAM policy for a `statements` service that may write only under `statements/` in the bucket, and associate it with a service account using EKS Pod Identity (Terraform: `aws_eks_pod_identity_association`).
3. Use the AWS Pricing Calculator to estimate the starter's environment for one day, one week and one month, with on-demand and spot nodes.
4. Compare deploying ITL Pay on EKS with a k3s VPS: cost, effort, what you lose, what you learn. Half a page.
5. Find the Nigeria Data Protection Act's rules on transferring personal data abroad and write two paragraphs for your ADR on where ITL Pay's data lives.

## Key terms

AWS account, region, availability zone, IAM, user, role, policy, least privilege, IAM Identity Center, EKS Pod Identity, IRSA, VPC, subnet, public and private subnet, internet gateway, NAT gateway, security group, EKS, managed node group, add-on, ECR, immutable tags, RDS, Multi-AZ, Secrets Manager, S3, lifecycle rule, MSK, AWS Budgets, cost allocation tags, spot instance, AKS, GKE, Autopilot, OpenShift, ROSA, data residency.

## Further reading

- [AWS: IAM best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
- [Amazon EKS best practices guide](https://docs.aws.amazon.com/eks/latest/best-practices/introduction.html)
- [Amazon EKS: Pod Identity](https://docs.aws.amazon.com/eks/latest/userguide/pod-identities.html)
- [Amazon EKS pricing](https://aws.amazon.com/eks/pricing/)
- [Amazon RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/)
- [AWS Budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html)
- [Nigeria Data Protection Commission](https://ndpc.gov.ng/)
