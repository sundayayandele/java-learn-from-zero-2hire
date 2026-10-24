## What this module gives you

Three weeks on the part of backend work that touches other teams and other companies: designing HTTP APIs that clients can rely on (week 14), documenting and versioning them with OpenAPI (week 14), consistent errors, pagination and idempotency so money is never moved twice (week 15), calling third-party APIs such as payment gateways and KYC providers safely with timeouts, retries, circuit breakers and signed webhooks (week 15), and knowing when GraphQL or gRPC is a better fit than REST (week 16).

Every project uses the Phase 1 baseline (Java 21, Spring Boot 4.1, Maven wrapper, PostgreSQL via Flyway, Problem Details, Actuator, CI) and adds an **OpenAPI contract** checked into the repository and **WireMock** stubs for every external API, so tests never depend on a live third party.

**Exit check for Module 5:** all five topic CBTs passed; project 5.A completed; one of 5.B–5.D optional (or start your capstones).

## Module projects

### Project 5.A: Payment gateway integration with webhooks (sandbox)

A merchant backend that takes card and bank-transfer payments through a payment gateway.

- **Gateway:** write against a **WireMock** stub of a Paystack/Flutterwave-style API (initialise transaction, verify transaction, refund). Optionally point the same client at a real gateway's free **test mode** with test keys; never live keys.
- **Flow:** `POST /api/v1/payments` (idempotent on `Idempotency-Key`) initialises a payment and returns the checkout URL; the gateway later calls `POST /api/v1/webhooks/gateway`, which you verify (HMAC-SHA512 signature over the raw body, constant-time compare), de-duplicate by event ID, and apply.
- **Rules:** a payment is only `SUCCEEDED` after webhook **and** server-side verification agree; amounts and currency must match what you initialised; webhooks may arrive late, twice or out of order.
- **Acceptance:** tests for a duplicate webhook, a forged signature (401), an amount mismatch, a gateway timeout during initialisation (retried once, then 503 Problem Detail), and a repeated `POST` with the same key (same response, one payment).

### Project 5.B: FX-rate or weather service consuming external APIs

- Consume the free [Frankfurter API](https://frankfurter.dev/) (ECB reference rates, no key) or the [Open-Meteo API](https://open-meteo.com/) (no key, CC BY 4.0 data) through a declarative `@HttpExchange` client.
- Add connect and read timeouts, retries with exponential backoff and jitter on 5xx and timeouts only, a circuit breaker, and a cache with a sensible TTL.
- Expose your own versioned, documented API on top (`/api/v1/...`), with Problem Details when the upstream is down.
- **Acceptance:** WireMock tests for slow responses (timeout fires), a 503 burst (circuit opens, then half-opens), and a 400 from upstream (not retried).

### Project 5.C: News aggregator from multiple sources

- Pull headlines from at least three public RSS/Atom feeds whose terms allow reuse (record each feed's terms in the README), normalise them into one model and de-duplicate by URL and title similarity.
- Fetch sources in parallel with virtual threads and a per-source timeout; a slow or failing source must not delay the response (return partial results with a `sources` status block).
- Cursor-based pagination over the merged, newest-first list, and an ETag on the list endpoint.

### Project 5.D: gRPC service for internal account lookups

- A `AccountLookup` gRPC service (`GetAccount`, `ResolveAccountName`, server-streaming `ListAccountsByCustomer`) defined in a `.proto` file, implemented with Spring gRPC, backed by PostgreSQL.
- A REST facade that calls it with a deadline, mapping gRPC status codes (`NOT_FOUND`, `DEADLINE_EXCEEDED`, `UNAVAILABLE`) to Problem Details.
- **Acceptance:** an in-process gRPC test for each RPC, and a test proving the facade returns 504 when the deadline is exceeded.

### Lab 5.E: Fix a fragile payment integration

A broken-service lab: [Fix a fragile payment integration](../../../labs/05-payment-client-resilience/README.md), five failing tests against a WireMock provider: timeouts, blind retries, idempotency, webhook signatures and leaked errors. Do it alongside the module; it counts as practice, not as the module project, but mentors review it if you submit it with your project.

## How these projects are assessed

| Criterion | Not yet | Competent | Strong |
| --- | --- | --- | --- |
| API design | Verbs in paths, 200 for everything | Resource-oriented paths, correct methods and status codes, versioned | Consistent conventions documented in the README, ETags and caching where they help |
| Contract and docs | No documentation | OpenAPI document generated or hand-written, matches the implementation | Contract checked in CI (breaking-change detection or contract tests) |
| Resilience | No timeouts | Timeouts, bounded retries on the right errors, circuit breaker | Retry budget and backoff reasoned about; behaviour under failure tested with WireMock |
| Money safety | Duplicate requests create duplicate payments | Idempotency keys and webhook de-duplication | Signature verification, amount checks and reconciliation against the provider |
| Testing and delivery | Manual testing only | WireMock-based tests for happy and failure paths, CI green | Failure-mode tests (timeouts, retries, out-of-order webhooks) run in CI |
