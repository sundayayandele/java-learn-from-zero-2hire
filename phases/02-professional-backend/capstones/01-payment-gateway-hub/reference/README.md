# Reference solution: Payment gateway integration hub

← [Capstone brief](../README.md) · [Build guide](../GUIDE.md) · [Architecture](../architecture/README.md)

> **Build your own version first.** This is one good solution, not the only one. Compare it with yours after you've finished, and be ready to explain where you chose differently and why.

Tested with Java 21, Spring Boot 4.1.2, Spring Security 7.1, Resilience4j 2.4, WireMock 3.13 and PostgreSQL 17: **64 tests, 93.6% line coverage** (the build enforces at least 80%).

## Run it

```bash
cp .env.example .env              # set DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, PROVIDER_A_SECRET, PROVIDER_B_SECRET
docker compose up --build         # app, PostgreSQL, Keycloak, two simulated providers
DEV_USER_PASSWORD='<pick one>' KEYCLOAK_ADMIN_PASSWORD='<same as .env>' ./scripts/dev-users.sh
TOKEN=$(curl -s -d client_id=itl-cli -d grant_type=password -d username=alice -d password="$DEV_USER_PASSWORD" \
  -d scope='openid api.read api.write' http://localhost:8081/realms/itl/protocol/openid-connect/token | jq -r .access_token)

curl -s -X POST http://localhost:8080/api/v1/payments -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"amount":250000,"currency":"NGN","email":"ada@example.ng"}' | jq
```

Simulate the provider's webhook (the signature is HMAC-SHA512 of the exact body with the provider secret):

```bash
BODY='{"event":"charge.success","data":{"id":"evt-1","reference":"pay_...","amount":250000,"currency":"NGN","status":"success"}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha512 -hmac "$PROVIDER_A_SECRET" | awk '{print $NF}')
curl -i -X POST http://localhost:8080/webhooks/provider-a -H "x-provider-signature: $SIG" -H 'Content-Type: application/json' -d "$BODY"
```

`./mvnw verify` runs everything (Docker must be running): unit tests, WireMock provider tests, and the end-to-end acceptance tests on a real PostgreSQL.

## How it's built

| Package | Responsibility |
| --- | --- |
| `payment` | `PaymentStateMachine` (the only place that knows legal transitions), `PaymentService` (initialise, verify, refund, apply), `WebhookService`, `ReconciliationJob`, JDBC repositories |
| `provider` | `PaymentProvider` interface, `HttpPaymentProvider` (`RestClient`, explicit timeouts, HTTP/1.1), `ProviderRouter` (one Resilience4j circuit breaker per provider, priority failover, Spring Framework 7 `RetryTemplate` for verify), `HmacSignatures` |
| `web` | Controllers, `ApiExceptionHandler` (Problem Details with `Retry-After` where useful) |
| `security` | Resource server, scopes and roles, `CurrentUser` (the merchant is the token subject) |
| `config` | `GatewayProperties`: providers, timeouts, breaker and retry settings, per-currency limits |

## Design notes

- **Provider calls never run inside a database transaction** except the single refund call, which deliberately holds the payment row lock so two concurrent refunds can't both pass the "not above the amount" check (the database `CHECK` is the second line of defence).
- **Idempotency:** the key is claimed with `INSERT ... ON CONFLICT DO NOTHING` before anything else happens. A concurrent duplicate waits briefly for the first request's stored response and replays it; if the first request failed before reaching a provider the key is released so the client can retry.
- **Webhooks:** signature first (over the raw bytes, constant-time comparison), then the `(provider, event id)` primary key makes duplicates a no-op, then the result is applied under `SELECT ... FOR UPDATE`. Amount or currency mismatches and successes for abandoned payments are recorded and counted (`payments.flagged`) for operations, never applied silently.
- **Failover is only for initialisation.** If Provider A times out after creating a transaction on its side, the customer never sees that authorisation URL, so nobody can pay it; we return Provider B's. Verify is retried (it is idempotent); initialise and refund are not.
- **Payment history is append-only**, enforced by a trigger, so support staff and auditors can trust it.
- Merchants get 404 for other merchants' references, so references can't be probed. `ROLE_OPS` can read every payment.

## What I'd do next

- A transactional outbox publishing `PaymentSucceeded` events (Phase 3).
- Per-merchant rate limiting on `POST /payments`.
- Distributed scheduling (ShedLock) if several instances run the reconciliation job, to avoid duplicate provider calls (correctness is already safe thanks to row locks).

Data: synthetic only (CC0). The provider contract is modelled on Paystack's public API; the optional Paystack test mode is subject to Paystack's terms.
