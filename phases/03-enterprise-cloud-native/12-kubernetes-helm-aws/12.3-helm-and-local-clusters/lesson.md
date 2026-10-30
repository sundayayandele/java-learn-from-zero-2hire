## What you will be able to do

- Explain what Helm adds over plain YAML, and when it's worth it.
- Read and write a chart: `Chart.yaml`, values, templates and helpers.
- Keep one chart for every environment with values files, and install, upgrade and roll back releases.
- Choose between kind, Minikube and k3d/k3s for local work, and run the whole ITL Pay system on kind.
- Test a chart before it reaches a cluster.

## Why this matters in a bank or fintech

ITL Pay's six services need about thirty Kubernetes objects, nearly identical except for names, ports and a few settings, in at least three environments (your laptop, staging and production). Copying YAML thirty times per environment guarantees drift: the production gateway gets the pre-stop fix, staging doesn't, and nobody notices until a release. **Helm** is the standard way to package that YAML once, with the differences in small values files. Most vendor software a bank installs on Kubernetes (Kafka operators, monitoring, Keycloak, Argo CD) also comes as Helm charts, so you'll read charts even when you don't write them.

## 1. What Helm is

**Helm** is a package manager for Kubernetes. A **chart** is a folder of templated YAML; installing it with a set of **values** creates a **release**, which Helm tracks so it can upgrade or roll it back. **Helm 4** (November 2025) is the current major version; charts written for Helm 3 (`apiVersion: v2`) work unchanged.

```
deploy/helm/itl-pay/
├── Chart.yaml            name, chart version, app version
├── values.yaml           defaults for every environment
├── values-local.yaml     kind on your laptop
├── values-aws.yaml       EKS
└── templates/
    ├── _helpers.tpl      named snippets reused by the templates
    ├── services.yaml     Deployment, Service, HPA and PDB for every service
    ├── interest-batch.yaml   the nightly CronJob
    ├── secret.yaml       database passwords (local only)
    └── route.yaml        the Gateway API HTTPRoute
```

```yaml
apiVersion: v2
name: itl-pay
description: ITL Pay services on Kubernetes (Phase 3 capstone starter, topic 12.3)
type: application
version: 0.3.0          # the chart's own version: bump it when the templates change
appVersion: "0.3.0"     # the version of the services it deploys
```

## 2. Values and templates

Templates are Go templates. Values come in as `.Values`; Helm also provides `.Release`, `.Chart` and functions such as `default`, `toYaml`, `quote` and `include`. ITL Pay's chart describes each service in a few lines of values:

```yaml
services:
  transfers:
    port: 8082
    database: transfers
    kafka: true
    replicas: 2
    env:
      ACCOUNTS_URL: http://accounts:8081
```

and one template loops over them all:

```yaml
{{- range $name, $svc := .Values.services }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $name }}
spec:
  {{- if not ($svc.autoscaling).enabled }}
  replicas: {{ $svc.replicas | default $.Values.defaults.replicas }}
  {{- end }}
  ...
          resources:
            {{- toYaml ($svc.resources | default $.Values.defaults.resources) | nindent 12 }}
{{- end }}
```

Things to notice:

- Inside `range`, `.` is the current item, so `$` reaches the chart's root (`$.Values`).
- `($svc.autoscaling).enabled` is safe when `autoscaling` is missing; `$svc.autoscaling.enabled` would fail.
- `{{-` trims whitespace before the tag; `nindent` adds a newline and indents, which keeps generated YAML valid.
- Repeated pieces live in `_helpers.tpl` as **named templates** (`itl-pay.labels`, `itl-pay.connections`, the security contexts) and are pulled in with `include`.

A whole service, such as the database settings, comes from one helper:

```yaml
{{- define "itl-pay.connections" -}}
{{- if .svc.database }}
- name: DB_URL
  value: jdbc:postgresql://{{ .root.Values.infra.postgresHost }}:5432/{{ .svc.database }}
- name: DB_USER
  value: {{ .svc.database }}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .root.Values.databases.secretName }}
      key: {{ .svc.database }}
{{- end }}
...
{{- end }}
```

## 3. One chart, many environments

Values files layer on top of each other, and later ones win. Locally:

```bash
helm upgrade --install itl-pay deploy/helm/itl-pay -n itl-pay \
     -f deploy/helm/itl-pay/values-local.yaml
```

`values-local.yaml` only says what's different on kind: create the development database Secret, enable the route. `values-aws.yaml` sets the ECR registry, the RDS and MSK addresses (from `terraform output`, topic 12.4), larger resources and more replicas, and leaves the Secret to External Secrets. The templates are identical everywhere, which is the point.

Keep secrets **out of values files** in Git. Locally a development password is fine; in shared environments use External Secrets (topic 12.1) or sealed/encrypted secrets.

## 4. Releases: install, upgrade, roll back

```bash
helm list -n itl-pay                         # releases and their revisions
helm history itl-pay -n itl-pay              # every revision, with status
helm upgrade itl-pay ... --wait --timeout 5m # wait until the new pods are ready
helm rollback itl-pay 3 -n itl-pay           # back to revision 3
helm uninstall itl-pay -n itl-pay
```

`--wait` makes the command fail if the new pods never become ready, so a CI pipeline notices a bad release. Each revision is stored in the cluster as a Secret, which is how `rollback` knows what revision 3 looked like. With GitOps (topic 12.5), Argo CD renders the chart itself and Git history becomes the rollback mechanism.

## 5. Testing a chart before it reaches a cluster

```bash
helm lint deploy/helm/itl-pay -f deploy/helm/itl-pay/values-local.yaml   # and with values-aws.yaml
helm template itl-pay deploy/helm/itl-pay -f deploy/helm/itl-pay/values-aws.yaml   # see exactly what would be applied
helm template ... | kubectl apply --dry-run=server -f -                   # let the API server validate it
```

The starter's CI lints the chart with both values files on every push. Teams often add a `values.schema.json` (Helm validates values against it), snapshot tests of `helm template` output, and a policy check (Kyverno or Conftest) that rejects pods running as root or without limits.

## 6. Local clusters: kind, Minikube, k3d

| | kind | Minikube | k3d / k3s |
| --- | --- | --- | --- |
| How | Kubernetes nodes as Docker containers | A VM or container with add-ons | Rancher's lightweight k3s, in Docker (k3d) or on a VM (k3s) |
| Strengths | Fast, scriptable, used by Kubernetes' own CI; multi-node | Friendly add-ons (dashboard, ingress) | Very light; k3s is a real option for a small VPS |
| Images | `kind load docker-image` | `minikube image load` | `k3d image import` |

ITL Pay uses **kind**: `deploy/kind/up.sh` creates the cluster, installs Envoy Gateway and metrics-server from pinned Helm charts, builds and loads the six images, applies PostgreSQL and Kafka, and installs the chart, from nothing to a working system in about 5–10 minutes. `kind delete cluster --name itl-pay` removes it all.

Mind your laptop's memory: measured on kind, the whole node uses about 4 GiB (ITL Pay's pods about 2 GiB, Envoy Gateway and metrics-server about 1.2 GiB, Kubernetes itself the rest). Give Docker Desktop at least 6 GiB.

## Worked example: changing the fraud limit in every environment

1. Product wants the fraud limit configurable per environment. You add `FRAUD_MAXAMOUNT` to fraud's `env` in `values.yaml` (`"20000"`) and override it in `values-aws.yaml` (`"1000000"`).
2. `helm lint` and `helm template` with both files show the variable in each rendering.
3. `helm upgrade` on kind with `values-local.yaml`; Helm changes only the fraud Deployment, which rolls its pod.
4. A ₦25,000 transfer is still rejected locally; in staging, with the AWS values, it clears.
5. A typo slips into the next change and fraud's new pod never becomes ready; `helm upgrade --wait` fails, and `helm rollback itl-pay` restores the previous revision in seconds.

## Common mistakes

- One chart per environment, or copies of YAML per environment, drifting apart.
- Secrets in values files committed to Git.
- Forgetting `$` inside `range`, or `nindent` with the wrong width, producing invalid YAML.
- Not bumping the chart `version` when templates change.
- `helm upgrade` without `--wait` in CI, so a broken release looks successful.
- Charts that set `replicas` while an HPA manages the Deployment.

## Practice

1. Run `deploy/kind/up.sh`, then `helm history itl-pay -n itl-pay`. Change the gateway's `maxReplicas`, upgrade, and roll back to the first revision.
2. Add a `values.schema.json` that requires `port` (integer) and allows only known keys for each service; show `helm lint` failing on a typo.
3. Add a `ConfigMap` template for fraud's settings and mount it, so changing the limit needs no image rebuild.
4. Write a CI step that renders the chart with both values files and fails if any container lacks resource requests (a short script over `helm template` output, or Kyverno CLI).
5. Recreate the cluster with two worker nodes (edit `deploy/kind/cluster.yaml`) and check how the scheduler spreads the gateway's pods.

## Key terms

Helm, chart, release, revision, `Chart.yaml`, chart version, app version, values, values file, template, Go templates, named template, `_helpers.tpl`, `include`, `toYaml`, `nindent`, `helm lint`, `helm template`, `--wait`, rollback, values schema, kind, Minikube, k3d, k3s, `kind load docker-image`.

## Further reading

- [Helm documentation](https://helm.sh/docs/)
- [Helm: chart template guide](https://helm.sh/docs/chart_template_guide/)
- [Helm: chart best practices](https://helm.sh/docs/chart_best_practices/)
- [kind quick start](https://kind.sigs.k8s.io/docs/user/quick-start/)
- [Minikube: get started](https://minikube.sigs.k8s.io/docs/start/)
- [k3s documentation](https://docs.k3s.io/)
