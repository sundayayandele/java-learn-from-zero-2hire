## What this module gives you

Two weeks on splitting a system into services without creating a distributed mess: finding the boundaries with domain-driven design and starting from a modular monolith (week 26), putting a gateway in front with configuration and discovery that suit Kubernetes (week 26), keeping one slow service from taking down the rest (week 27), and keeping money consistent when there's no longer one database transaction (week 27).

All code runs on Spring Boot 4.1 with **Spring Cloud 2025.1** and **Spring Modulith 2.1**. The [ITL Pay starter](../../../templates/itl-pay-starter/README.md) contains every lesson's code, tested: a gateway, an accounts service with idempotent holds and Modulith modules, and a transfers service with a circuit breaker.

**Weekly rhythm (20–25 hours):** read the lessons and code along (30%), videos (10%), topic quizzes (5%), the module project (20%) and your capstone system's `v0.1` (35%).

**Exit check for Module 10:** the four topic quizzes attempted (aim for 70%+), one module project below, and capstone release `v0.1` tagged with ADR 1 and ADR 2.

## Capstone step: ITL Pay `v0.1`

Start from the [ITL Pay starter](../../../templates/itl-pay-starter/README.md) and deliver:

- **Services:** gateway, accounts, transfers and a new **ledger** service, each with its own PostgreSQL database (or schema and user) and Flyway migrations. No service touches another's tables.
- **Transfer flow:** `REQUESTED → RESERVED → POSTED`, or `REJECTED` / `FAILED` with the hold released, persisted after each step; every call idempotent by transfer reference, enforced by unique constraints.
- **Resilience:** timeouts on every call (each shorter than its caller's), a circuit breaker per dependency that ignores business rejections, and honest fallbacks written in an ADR.
- **Security:** JWT validation at the gateway and in each service, with your Phase 2 Keycloak realm.
- **Tests:** a Spring Modulith `verify()` test where you use modules; concurrency tests proving one hold per reference; Testcontainers tests for each service's repository layer.
- **Docs:** ADR 1 (context map and service split, including what you didn't split), ADR 2 (transfer states and compensations), ADR 3 (fallbacks), and a README with a diagram and a `docker compose up` quick start.

## Module projects

Pick **at least one**, in addition to your capstone step.

### Project 10.A: Banking services (Account, Transaction, Notification)

A smaller system to practise the split itself, in a domain different from your capstone: a savings-and-loans cooperative.

- **Contexts:** Members, Contributions (monthly savings), Loans (apply, approve, repay) and Notifications.
- **Start as a modular monolith** with Spring Modulith; publish `ContributionReceived` and `LoanApproved` events between modules, with a `verify()` test.
- **Then extract one module** (Notifications) into its own service using the strangler fig: a gateway route, an HTTP or event contract, and the module deleted from the monolith.
- **Acceptance:** the `verify()` test passes before and after; the extracted service can be down without failing contributions (honest fallback); a short ADR explains why Notifications was extracted first.

### Project 10.B: E-commerce services (User, Product, Order, Payment)

- **Services:** Users, Catalogue, Orders and Payments, behind a gateway with a rate limit on `POST /api/orders`.
- **Order flow:** reserve stock, authorise payment (simulated, with a configurable failure rate and delay), confirm; on payment failure, release the stock. Every step idempotent by order id.
- **Resilience:** run a load test while the payment simulator fails 30% of calls and slows to 5 s; the breaker must open, orders must fail fast with a clear message, and catalogue browsing must stay fast.
- **Acceptance:** no order ends with stock reserved and no payment after the reconciliation job runs; a dashboard or log shows the breaker opening and closing.

### Project 10.C: Lab: find the distributed monolith

Take one of your Phase 2 capstones and draw its context map honestly. Identify every place where a split would create a shared database, a synchronous chain longer than two calls, or a release dependency. Write a one-page recommendation: which part (if any) should become a service, and which should stay as modules. There's no code to submit, only the map and the argument; your mentor reviews it like an architecture review.
