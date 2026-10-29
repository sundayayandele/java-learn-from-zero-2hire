# ITL Pay starter (Phase 3 capstone system)

← [Phase 3](../../phases/03-enterprise-cloud-native/README.md) · [Module 10](../../phases/03-enterprise-cloud-native/10-microservices-distributed-systems/README.md)

The starting point for the Phase 3 capstone system: three Spring Boot 4.1 services (Java 21) built by one Maven command, with Spring Cloud 2025.1 and Spring Modulith 2.1. Every piece is the code from Module 10's lessons, tested.

| Service | Port | What it does now |
| --- | --- | --- |
| `gateway` | 8080 | Spring Cloud Gateway (server WebMvc): routes `/api/accounts/**` and `/api/transfers/**`, strips the `/api` prefix |
| `accounts` | 8081 | Balances and **idempotent holds** (`POST /accounts/{id}/holds`, 201 then 200 for the same reference, 422 when funds are short); a Spring Modulith `notification` module listens to `FundsReserved` |
| `transfers` | 8082 | `POST /transfers` reserves funds in accounts through a **circuit breaker** with timeouts; answers 503 "not started" when accounts is down, and passes 422 through without tripping the breaker |

```bash
./mvnw verify                                   # build and test all three
./mvnw -q package -DskipTests && docker compose up --build
curl -s localhost:8080/api/accounts/A1
curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
     -d '{"reference":"TRF-1","from":"A1","to":"A2","amount":2500.00}'
curl -s localhost:8082/actuator/circuitbreakers  # inside the network; or publish the port while experimenting
```

Balances are in memory with two demo accounts (A1 ₦50,000, A2 ₦1,000), so the starter runs with nothing else installed.

## Your `v0.1` (Module 10)

Copy this folder into your own repository, then:

1. Give **accounts** and **transfers** their own PostgreSQL databases (or schemas with separate users) with Flyway migrations: the `hold` table with `UNIQUE (account_id, reference)` from topic 10.4, and a `transfer` table with the state machine.
2. Add a **ledger** service that posts balanced double-entry journals, idempotent by transfer reference (reuse your Phase 1 Capstone 1 ledger).
3. Complete the transfer flow: `REQUESTED → RESERVED → POSTED`, with `REJECTED` and `FAILED` (release the hold), persisted after each step.
4. Validate JWTs at the gateway **and** in each service with your Phase 2 Keycloak realm.
5. Write ADR 1 (context map and service split) and ADR 2 (transfer states and compensations).
6. Tag `v0.1`. CI must build and test every service on each push.

Module 11 replaces the direct calls in step 3 with events through a transactional outbox.
