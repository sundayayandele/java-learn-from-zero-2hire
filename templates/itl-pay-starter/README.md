# ITL Pay starter (Phase 3 capstone system)

← [Phase 3](../../phases/03-enterprise-cloud-native/README.md) · [Module 10](../../phases/03-enterprise-cloud-native/10-microservices-distributed-systems/README.md) · [Module 11](../../phases/03-enterprise-cloud-native/11-event-driven-kafka-batch/README.md) · [Module 12](../../phases/03-enterprise-cloud-native/12-kubernetes-helm-aws/README.md) · [Module 13](../../phases/03-enterprise-cloud-native/13-observability-performance/README.md) · [Module 14](../../phases/03-enterprise-cloud-native/14-reactive-high-throughput/README.md)

The starting point for the Phase 3 capstone system: Spring Boot 4.1 services (Java 21) built by one Maven command, with Spring Cloud 2025.1, Spring Modulith 2.1, Spring for Apache Kafka 4.1 and Spring Batch 6. It holds the code from every lesson so far, tested.

| Service | Port | What it does now | From |
| --- | --- | --- | --- |
| `gateway` | 8080 | Spring Cloud Gateway (server WebMvc): routes `/api/accounts/**` and `/api/transfers/**`, strips the `/api` prefix | 10.2 |
| `accounts` | 8081 | Balances and **idempotent holds** (`POST /accounts/{id}/holds`: 201, then 200 for the same reference, 422 when funds are short) and their **release** (`DELETE /accounts/{id}/holds/{reference}`); a Spring Modulith `notification` module | 10.1, 10.4, 11.3 |
| `transfers` | 8082 | The **transfer saga**: `POST /transfers` records the transfer, reserves funds through a **circuit breaker**, and writes each state change and its event in one transaction to a **transactional outbox**, which a relay publishes to Kafka. `GET /transfers/{reference}` shows the state | 10.3, 11.3 |
| `fraud` | 8083 | Reacts to `TransferReserved`, publishes `CLEARED` or `REJECTED` (over ₦20,000) to `fraud-decisions`; **retry topics** and a dead-letter topic | 11.2, 11.4 |
| `notifications` | 8084 | An **idempotent consumer**: one SMS per event even when Kafka delivers twice; blocking retries, then a **dead-letter topic** | 11.3, 11.4 |
| `interest-batch` | – | **Spring Batch** end-of-day interest posting for 1,000 savings accounts, in chunks of 100, **restartable** and idempotent per account and day | 11.5 |
| `rates` | 8085 | **Reactive** FX rates: WebFlux, `WebClient` and R2DBC; the latest rates, a **server-sent-events** stream with backpressure, and quotes valid for 30 seconds (`/api/rates/**`, `/api/quotes`) | 14.1, 14.2 |
| `rates-mvc` | 8086 | The same benchmark endpoint on Spring MVC with **virtual threads** (or platform threads), for comparison; not part of the running system | 14.3 |

PostgreSQL 18 (one database and user per service), Kafka 4.1 (one KRaft broker), Redis 8 and Grafana's all-in-one observability image (Grafana, Prometheus, Tempo) run in Docker Compose. The tests start their own PostgreSQL, Kafka and Redis with Testcontainers, so `./mvnw verify` needs Docker running.

```bash
./mvnw verify                                   # build and test every service
./mvnw -q package -DskipTests && docker compose up --build

curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
     -d '{"reference":"TRF-1","from":"A1","to":"A2","amount":2500.00}'    # cleared by fraud
curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
     -d '{"reference":"TRF-2","from":"A1","to":"A2","amount":25000.00}'   # rejected: the hold is released
curl -s localhost:8080/api/transfers/TRF-2                               # "state":"FAILED"
curl -s localhost:8080/api/accounts/A1                                   # back to 47500.00
docker compose logs notifications | grep SMS

# Watch the events
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
     --topic transfer-events --from-beginning --property print.key=true

# Observability (Module 13): Grafana at http://localhost:3300 (admin / admin), dashboard "ITL Pay";
# traces in Explore -> Tempo; alert rules in Alerting. Logs are JSON with trace ids:
docker compose logs notifications | grep SMS

# Live FX rates (Module 14): server-sent events through the gateway, and a quote
curl -N 'localhost:8080/api/rates/stream?pair=USD-NGN'
curl -s -X POST localhost:8080/api/quotes -H 'Content-Type: application/json' -d '{"pair":"GBP-NGN","amount":250}'

# Load test (Module 13): about 3.5 minutes, up to 60 transfers a second
docker run --rm --network itl-pay-starter_default -v "$PWD/load:/scripts" grafana/k6:2.3.0 run /scripts/transfers.js

# End-of-day interest: make it fail part-way, then rerun the same day to restart it
docker compose run --rm -e INTEREST_FAILONACCOUNT=SAV-0457 interest-batch businessDate=2026-10-30   # exit code 5
docker compose run --rm interest-batch businessDate=2026-10-30                                     # restarts at account 401
```

Balances in `accounts` are still in memory with two demo accounts (A1 ₦50,000, A2 ₦1,000), so the flow is easy to follow.

## Reactive versus virtual threads (Module 14)

The benchmark runs on its own (stop the main stack first), one variant at a time, each with 2 CPUs and 768 MB, against a provider that answers after 1 second:

```bash
docker compose -f compose.yaml -f load/bench.compose.yaml up -d --build postgres fx-provider rates rates-mvc rates-mvc-platform
docker run --rm --network itl-pay-starter_default -v "$PWD/load:/scripts" -e TARGET=http://rates:8085 -e RATE=300 grafana/k6:2.3.0 run /scripts/bench.js
#   TARGET=http://rates-mvc:8086 (virtual threads) or http://rates-mvc-platform:8086 (200 platform threads)
```

At 300 requests a second, reactive and virtual threads both served about 280 a second at about 1 s, while 200 platform threads served about 190 a second at about 3 s: Little's law. Topic 14.3 has the full results, including what happened under overload.

## Observability and performance (Module 13)

| Path or piece | What it is | Topic |
| --- | --- | --- |
| `spring-boot-starter-opentelemetry` in every service | Traces over OTLP to Tempo, across HTTP, the circuit breaker, Spring Modulith and Kafka | 13.1 |
| `outbox.traceparent` | The outbox stores each event's trace context and the relay continues it, so one trace covers the whole saga (tested) | 13.1 |
| `itlpay_transfers_total`, `itlpay_outbox_oldest_age_seconds` | Business metrics: transfers by state, and how long the oldest event has waited to leave the outbox | 13.1, 13.2 |
| `observability/` | Prometheus scrape config, alert rules (SLO burn rate, slow transfers, outbox stuck, breaker open, consumer lag) and the provisioned Grafana dashboard | 13.2, 13.5 |
| `CacheConfig`, `Transfers.status` | Redis cache-aside for the status apps poll, evicted after commit, falling back to the database if Redis is down (tested) | 13.4 |
| `OutboxRelay` | Pipelined batch sends: the load test found the one-at-a-time relay 45 s behind at 60 transfers a second; now about 3 s | 13.5 |
| `load/transfers.js` | The k6 load test with the SLO thresholds | 13.5 |

Profile a running service with Flight Recorder from a JDK debug container (topic 13.3):

```bash
G=$(docker exec itl-pay-starter-accounts-1 id -g)
docker run --rm --pid=container:itl-pay-starter-accounts-1 --user 10001:$G eclipse-temurin:21-jdk \
    jcmd 1 JFR.start name=load settings=profile filename=/tmp/accounts.jfr
# ... run the load test, then JFR.stop name=load, docker cp the file out, and: jfr view contention-by-site accounts.jfr
```

## On Kubernetes (Module 12)

`deploy/` runs the same system on Kubernetes, tested on kind (Kubernetes 1.37) with Helm 4, Envoy Gateway 1.9 and Argo CD 3.5:

| Path | What it is | Topic |
| --- | --- | --- |
| `deploy/kind/up.sh` | Creates a local cluster and deploys everything with one command (5–10 minutes; about 4 GiB of memory) | 12.3 |
| `deploy/kind/gateway.yaml` | The Gateway API gateway (Envoy Gateway) that lets traffic into the cluster | 12.1 |
| `deploy/infra/` | Plain manifests for PostgreSQL and Kafka as StatefulSets, and Redis, for kind only | 12.1 |
| `deploy/helm/itl-pay/` | The Helm chart for the six services: probes, requests and limits, security contexts, autoscaling, disruption budgets, the nightly interest CronJob and the HTTPRoute; `values-local.yaml` and `values-aws.yaml` | 12.1–12.3 |
| `deploy/terraform/aws/` | Terraform for AWS: VPC, EKS, RDS PostgreSQL 18, a statements bucket, ECR repositories and a budget alarm. Validated, not applied: applying it costs money | 12.4, 12.5 |
| `deploy/argocd/` | An Argo CD Application that deploys the chart from Git | 12.5 |

```bash
deploy/kind/up.sh                               # needs Docker, kind, kubectl and Helm
kubectl -n envoy-gateway-system port-forward \
    svc/$(kubectl -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name=itl-pay -o name | cut -d/ -f2) 8088:80
curl -s localhost:8088/api/accounts/A1
kubectl -n itl-pay create job eod-now --from=cronjob/interest-batch    # run tonight's interest job now
kind delete cluster --name itl-pay              # remove everything
```

## Your `v0.1` (Module 10)

Copy this folder into your own repository, then:

1. Give **accounts** its own PostgreSQL database and user with Flyway migrations: the `hold` table with `UNIQUE (account_id, reference)` from topic 10.4 (transfers already has its database; see its migration).
2. Add a **ledger** service that posts balanced double-entry journals, idempotent by transfer reference (reuse your Phase 1 Capstone 1 ledger).
3. Complete the transfer flow up to `POSTED`, with `REJECTED` and `FAILED` (release the hold), persisted after each step.
4. Validate JWTs at the gateway **and** in each service with your Phase 2 Keycloak realm.
5. Write ADR 1 (context map and service split) and ADR 2 (transfer states and compensations).
6. Tag `v0.1`. CI must build and test every service on each push.

## Your `v0.2` (Module 11)

1. Make the ledger a saga step: after `CLEARED`, transfers asks the ledger to post through an event (`PostJournal`), the ledger answers `JournalPosted` or `PostingFailed`, and transfers moves to `POSTED` or compensates. Every message goes through an outbox and every consumer is idempotent.
2. Add a scheduled **stuck-transfer sweeper**: transfers in `REQUESTED` or `RESERVED` for more than 15 minutes are retried or compensated, and counted in a metric.
3. Give each event type a **versioned JSON Schema** in `docs/events/`, add a field in a backward-compatible way, and prove an old consumer still reads the new event.
4. Write the **dead-letter runbook**: how you alert on a DLT, inspect a message, fix and **replay** it (a small replay command or endpoint), with a test.
5. Point **interest-batch** at your ledger's balances (a read model fed by `JournalPosted` events, or the ledger's API), and post interest as journals, still one per account per day.
6. Write ADR 4 (events and the outbox: why not "save, then send") and ADR 5 (saga style: why Transfers orchestrates). Tag `v0.2`.

## Your `v0.3` (Module 12)

1. Add your ledger (and accounts' database) to the chart, and give accounts a second replica once its balances are in PostgreSQL.
2. Run the whole system on kind from one script, and record a rolling restart under load with no failed requests.
3. Load-test the gateway and transfers, and show the HPA scaling up and back down.
4. Apply the Terraform (or your own for AKS, GKE or a k3s VPS) with remote state and a budget alarm first; deploy with `values-aws.yaml`, passwords synced from the secrets manager by External Secrets; record the evidence; destroy it the same day.
5. Point an Argo CD Application at your repository, and show a merged change reaching the cluster with nobody running `helm` or `kubectl`.
6. Write ADR 6 (running on Kubernetes: probes, resources, scaling) and ADR 7 (cloud, Terraform and GitOps, with the cost). Tag `v0.3`.

## Your `v0.4` (Module 13)

1. Extend tracing to your ledger, so one trace covers the whole saga up to `POSTED`; add a timer for the saga's duration.
2. Add your own business metrics and panels (amounts by outcome, saga duration p95), and keep the dashboard in Git.
3. Write two SLOs for `POST /api/transfers` (availability and latency), with burn-rate alerts and a runbook for each paging alert.
4. Remove accounts' global lock (per-account locking in PostgreSQL), and prove the gain with Flight Recorder and the load test.
5. Load-test at your expected peak and twice it; write the report (throughput, percentiles, errors, the bottleneck, the fix, before and after).
6. Write ADR 8 (observability choices) and a short postmortem of one problem the load test found. Tag `v0.4`.

## Your `v0.5` (Module 14)

1. Make foreign-currency transfers use a quote: the request carries a quote id from `rates`, and transfers checks it hasn't expired before reserving funds.
2. Add a limit on work in progress (a semaphore, bulkhead or gateway rate limit) to whichever services face slow dependencies, and prove with k6 that overload produces fast `503`s, flat memory and a responsive health check.
3. Run the benchmark on your own machine: find your CPU ceiling first, then compare the three variants below and above it; write the report.
4. Show the rates stream in a small web page with `EventSource`, and test a slow client's behaviour.
5. Write ADR 9: which ITL Pay services are reactive, which use virtual threads, and why, with your numbers. Tag `v0.5`.
