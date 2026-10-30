## What you will be able to do

- Describe infrastructure as code with Terraform: providers, resources, modules, variables and outputs.
- Run the plan-and-apply workflow safely, with remote state and locking.
- Run Terraform from CI without long-lived cloud keys, with plans reviewed in pull requests.
- Deploy with GitOps: Argo CD keeps a cluster equal to what's in Git.
- Promote a release from one environment to the next through Git.

## Why this matters in a bank or fintech

Auditors ask two questions about every production change: who approved it, and what exactly changed. "An engineer clicked in the console" answers neither. With **Terraform**, infrastructure changes are code reviewed in pull requests, and the plan shows exactly what will change before it does. With **GitOps**, deployments are commits: the cluster runs what `main` says, every change has an author and an approval, and rolling back is a revert. Banks adopted both largely because they make change management provable.

## 1. Terraform in five ideas

1. **Providers** talk to APIs (AWS, Azure, Kubernetes, GitHub). The starter pins `hashicorp/aws ~> 6.0`.
2. **Resources** are things to create: `aws_db_instance`, `aws_s3_bucket`.
3. **Modules** are reusable groups of resources. The starter uses the community `terraform-aws-modules/vpc` and `terraform-aws-modules/eks` modules rather than hundreds of lines of networking.
4. **Variables** go in, **outputs** come out: `budget_email` in; `postgres_host` and `image_registry` out, which feed the Helm values.
5. **State** records what Terraform created, so it can work out what to change next time.

```hcl
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0"

  name               = var.name
  kubernetes_version = "1.35"
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    default = {
      instance_types = ["t3.large"]
      capacity_type  = "SPOT"
      min_size       = 2
      max_size       = 3
      desired_size   = 2
    }
  }
}
```

**OpenTofu** is the open-source fork created after HashiCorp moved Terraform to the Business Source Licence in 2023; it reads the same code, and some banks prefer it for licensing reasons.

## 2. The workflow

```bash
cd deploy/terraform/aws
terraform init -backend-config=backend.hcl   # download providers and modules, connect to the state
terraform fmt -check && terraform validate   # style and correctness, no cloud access needed
terraform plan -out=plan.tfplan              # what WILL change: create, update in place, destroy and recreate
terraform apply plan.tfplan                  # do exactly that plan
terraform output                             # values for the Helm chart
terraform destroy                            # remove everything (learning environments only)
```

**Read every plan.** A line such as `-/+ resource "aws_db_instance" ... (forces replacement)` means the database will be **deleted and recreated**. Production databases get `deletion_protection = true` and `prevent_destroy` lifecycle rules so a careless change can't do that.

The starter's Terraform passes `terraform validate` and `fmt` (the repository's CI checks both), and its versions were resolved against the current modules (EKS module 21.x, VPC module 6.x, AWS provider 6.x). It hasn't been applied to an AWS account by us: applying it costs money and needs your account, so that's your `v0.3` step.

## 3. Remote state and locking

State on one laptop is a single point of failure, and two people applying at once corrupt it. Keep it remote and locked:

```hcl
backend "s3" {
  key          = "itl-pay/terraform.tfstate"
  use_lockfile = true      # Terraform 1.10+: a lock file in S3, no DynamoDB table needed
  encrypt      = true
}
```

The bucket and region come from `backend.hcl`, which isn't committed. Turn on **versioning** for the state bucket so a bad apply can be recovered. State can contain secrets, which is one reason the starter lets RDS manage the master password in Secrets Manager instead of passing one in.

Commit **`.terraform.lock.hcl`**, which pins provider versions so everyone (and CI) gets the same; ignore `.terraform/`, state files and `*.tfvars`.

## 4. Terraform in CI, without keys

1. A pull request runs `fmt`, `validate` and **`plan`**, and posts the plan as a comment for review.
2. Merging to `main` runs **`apply`** of that reviewed change, with an environment approval for production.
3. CI authenticates with **GitHub's OIDC token**: AWS trusts GitHub for a specific repository and branch, and CI assumes a role for the job's duration. No access keys stored in GitHub secrets.

```yaml
permissions:
  id-token: write      # let the job request an OIDC token
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: arn:aws:iam::123456789012:role/itl-pay-terraform
      aws-region: eu-west-1
  - uses: hashicorp/setup-terraform@v3
  - run: terraform init -backend-config=backend.hcl && terraform plan -no-color
```

## 5. GitOps with Argo CD

**GitOps** means the desired state of the cluster lives in Git, and an agent in the cluster continuously makes the cluster match it. Nobody runs `helm upgrade` or `kubectl apply` against production; they open pull requests.

**Argo CD** is the most common agent. An **Application** says where the desired state is and where it goes:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: itl-pay-local
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/sundayayandele/java-learn-from-zero-2hire.git
    targetRevision: main
    path: templates/itl-pay-starter/deploy/helm/itl-pay
    helm:
      valueFiles:
        - values-local.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: itl-pay
  syncPolicy:
    automated:
      prune: true       # delete what was removed from Git
      selfHeal: true    # undo changes made by hand with kubectl
```

- Argo CD renders the Helm chart itself and compares the result with the cluster. **OutOfSync** means they differ; **Synced** and **Healthy** means the cluster matches Git and everything is running.
- **selfHeal** reverts manual changes: scale fraud to three pods by hand, and Argo CD scales it back to what Git says. That's the property auditors like.
- **prune** removes objects that were deleted from Git.

**How a new version reaches production:** CI builds and pushes `itl-pay/transfers:0.3.1`, then opens a pull request changing `image.tag` in the staging values. Merge, and Argo CD rolls staging. Promotion to production is another pull request changing the production values. Every deployment has a commit, an author, a reviewer and a revert button. (Argo CD Image Updater can automate the tag pull requests; **Flux** is the other widely used GitOps agent.)

## 6. What goes where

| Layer | Tool | Changes |
| --- | --- | --- |
| Cloud resources: VPC, cluster, database, buckets | Terraform | Rarely; reviewed plans |
| Cluster platform: Envoy Gateway, External Secrets, monitoring | Argo CD (Helm charts) | Occasionally |
| Applications: ITL Pay | Argo CD (your chart) | Many times a day |

Keep them separate: Terraform shouldn't deploy applications, and application pull requests shouldn't be able to change the VPC.

## Worked example: a fraud-limit change, start to finish

1. A developer opens a pull request changing fraud's limit in `values-aws.yaml`. CI lints the chart and renders it with both values files.
2. A reviewer approves; it's merged to `main`.
3. Within three minutes Argo CD notices the new commit, shows the fraud Deployment as **OutOfSync**, syncs it, and the fraud pod rolls with the new setting.
4. Someone on call "fixes" an incident by editing the Deployment with `kubectl`. Argo CD's self-heal reverts it and the change is visible in its history, so the team writes the fix as a pull request instead.
5. The limit turns out to be wrong; `git revert` of the merge commit puts the old value back the same way.

## Common mistakes

- Terraform state on a laptop, or in Git.
- Applying without reading the plan, and replacing a database.
- Long-lived AWS keys in CI secrets instead of OIDC.
- Clicking changes in the console that Terraform then reverts (or worse, doesn't know about).
- Running `helm upgrade` by hand against a cluster that Argo CD manages; it'll be reverted.
- Secrets committed to the GitOps repository.
- One Terraform configuration for everything, so a small application change risks the network.

## Practice

1. Run `terraform init -backend=false`, `validate` and `fmt -check` on the starter's Terraform, then add an `aws_ecr_lifecycle_policy` that keeps only the last 20 images per repository, and validate again.
2. Write the GitHub Actions workflow that plans on pull requests and applies on `main` using OIDC (you can write it fully before you have an AWS account).
3. Install Argo CD on kind (`helm install argocd argo/argo-cd -n argocd --create-namespace`), point the Application at **your** repository, and watch it sync ITL Pay.
4. With self-heal on, scale fraud to 3 replicas with `kubectl` and time how long Argo CD takes to put it back.
5. Write ADR 7: Terraform or OpenTofu, Argo CD or Flux, and how a release is promoted from staging to production.

## Key terms

Infrastructure as code, Terraform, OpenTofu, provider, resource, module, variable, output, state, remote state, state locking, `use_lockfile`, `.terraform.lock.hcl`, plan, apply, destroy, forces replacement, `prevent_destroy`, OIDC, GitOps, Argo CD, Application, sync, OutOfSync, Healthy, prune, self-heal, Flux, promotion, Image Updater.

## Further reading

- [Terraform: get started with AWS](https://developer.hashicorp.com/terraform/tutorials/aws-get-started)
- [Terraform: S3 backend](https://developer.hashicorp.com/terraform/language/backend/s3)
- [terraform-aws-modules: EKS module](https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest)
- [GitHub Actions: configuring OpenID Connect in AWS](https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)
- [Argo CD documentation](https://argo-cd.readthedocs.io/en/stable/)
- [OpenGitOps principles](https://opengitops.dev/)
- [OpenTofu](https://opentofu.org/)
