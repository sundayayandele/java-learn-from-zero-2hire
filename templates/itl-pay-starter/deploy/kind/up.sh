#!/usr/bin/env bash
# Creates the local ITL Pay cluster on kind and deploys everything (topic 12.3).
# Needs Docker, kind, kubectl and Helm. Takes 5-10 minutes the first time.
# Tear it all down with: kind delete cluster --name itl-pay
set -euo pipefail
cd "$(dirname "$0")/../.."          # templates/itl-pay-starter
TAG=0.5.0
SERVICES="accounts transfers gateway fraud notifications interest-batch rates"

echo "== cluster"
kind create cluster --config deploy/kind/cluster.yaml

echo "== platform: Envoy Gateway (Gateway API) and metrics-server (for autoscaling)"
helm install eg oci://docker.io/envoyproxy/gateway-helm --version 1.9.3 \
     -n envoy-gateway-system --create-namespace --wait --timeout 6m
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ --force-update >/dev/null
helm install metrics-server metrics-server/metrics-server --version 3.14.0 \
     -n kube-system --set 'args={--kubelet-insecure-tls}' --wait   # kind's kubelets use self-signed certificates

echo "== images: build, then copy into the cluster (no registry needed)"
./mvnw -q package -DskipTests
for s in $SERVICES; do docker build -q -t "itl-pay/$s:$TAG" "$s" >/dev/null; done
# shellcheck disable=SC2046
kind load docker-image --name itl-pay $(for s in $SERVICES; do echo "itl-pay/$s:$TAG"; done)

echo "== PostgreSQL, Kafka and Redis"
kubectl create namespace itl-pay
kubectl -n itl-pay apply -f deploy/infra/
kubectl -n itl-pay rollout status statefulset/postgres --timeout=5m
kubectl -n itl-pay rollout status statefulset/kafka --timeout=5m
kubectl -n itl-pay rollout status deployment/redis --timeout=5m

echo "== ITL Pay"
kubectl apply -f deploy/kind/gateway.yaml
helm upgrade --install itl-pay deploy/helm/itl-pay -n itl-pay \
     -f deploy/helm/itl-pay/values-local.yaml --wait --timeout 8m
kubectl -n itl-pay get pods

cat <<'DONE'

Ready. Reach the API through the Gateway:
  kubectl -n envoy-gateway-system port-forward \
      svc/$(kubectl -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name=itl-pay -o name | cut -d/ -f2) 8088:80
  curl -s localhost:8088/api/accounts/A1
DONE
