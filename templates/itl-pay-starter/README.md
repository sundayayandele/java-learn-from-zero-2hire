# ITL Pay starter (Phase 3 capstone system)

← [Phase 3](../../phases/03-enterprise-cloud-native/README.md) · [Module 10](../../phases/03-enterprise-cloud-native/10-microservices-distributed-systems/README.md) · [Module 11](../../phases/03-enterprise-cloud-native/11-event-driven-kafka-batch/README.md)

The starting point for the Phase 3 capstone system: Spring Boot 4.1 services (Java 21) built by one Maven command, with Spring Cloud 2025.1, Spring Modulith 2.1, Spring for Apache Kafka 4.1 and Spring Batch 6. It holds the code from every lesson so far, tested.

| Service | Port | What it does now | From |
| --- | --- | --- | --- |
| `gateway` | 8080 | Spring Cloud Gateway (server WebMvc): routes `/api/accounts/**` and `/api/transfers/**`, strips the `/api` prefix | 10.2 |
| `accounts` | 8081 | Balances and **idempotent holds** (`POST /accounts/{id}/holds`: 201, then 200 for the same reference, 422 when funds are short) and their **release** (`DELETE /accounts/{id}/holds/{reference}`); a Spring Modulith `notification` module | 10.1, 10.4, 11.3 |
| `transfers` | 8082 | The **transfer saga**: `POST /transfers` records the transfer, reserves funds through a **circuit breaker**, and writes each state change and its event in one transaction to a **transactional outbox**, which a relay publishes to Kafka. `GET /transfers/{reference}` shows the state | 10.3, 11.3 |
| `fraud` | 8083 | Reacts to `TransferReserved`, publishes `CLEARED` or `REJECTED` (over ₦20,000) to `fraud-decisions`; **retry topics** and a dead-letter topic | 11.2, 11.4 |
| `notifications` | 8084 | An **idempotent consumer**: one SMS per event even when Kafka delivers twice; blocking retries, then a **dead-letter topic** | 11.3, 11.4 |
| `interest-batch` | – | **Spring Batch** end-of-day interest posting for 1,000 savings accounts, in chunks of 100, **restartable** and idempotent per account and day | 11.5 |

PostgreSQL 18 (one database and user per service) and Kafka 4.1 (one KRaft broker) run in Docker Compose. The tests start their own PostgreSQL and Kafka with Testcontainers, so `./mvnw verify` needs Docker running.

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

# End-of-day interest: make it fail part-way, then rerun the same day to restart it
docker compose run --rm -e INTEREST_FAILONACCOUNT=SAV-0457 interest-batch businessDate=2026-10-30   # exit code 5
docker compose run --rm interest-batch businessDate=2026-10-30                                     # restarts at account 401
```

Balances in `accounts` are still in memory with two demo accounts (A1 ₦50,000, A2 ₦1,000), so the flow is easy to follow.

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
