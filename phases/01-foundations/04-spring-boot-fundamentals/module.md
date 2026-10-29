## What this module gives you

Four weeks to build real Spring Boot 4 services: how Spring wires an application and how HTTP and REST work (week 11), REST endpoints with validation and clean errors, and persistence with JPA without the classic performance traps (week 12), configuration, secrets and production health/metrics (week 13), and the Boot 3 → 4 upgrade and what Spring does under the hood (week 14). Topic 4.9 (Spring Boot with Kotlin) is **optional**. Week 14 also hosts the end-of-phase exam and the Getting Hired session.

Every project in this module uses the same baseline: Java 21, Spring Boot 4.1.x, Maven wrapper, PostgreSQL 17 via Docker Compose, Flyway migrations, `ddl-auto=validate`, Problem Details errors, Actuator, and a GitHub Actions workflow (plus the optional GitLab CI file). The [Spring Boot service starter](../../../templates/spring-boot-service-starter/README.md) has all of that ready.

**Exit check for Module 4:** the eight core topic CBTs attempted (aim for 70%+); projects 4.A and 4.D completed; 4.B or 4.C optional (or start your capstones instead).

## Module projects

### Project 4.A: Task manager REST API (CRUD, validation)

A back-office task tracker for a bank's operations team (for example "verify KYC documents for case 913").

- Endpoints: create, get, list (filter by status and assignee), update, change status, delete (soft delete only).
- `Task` has title, description, priority, status (OPEN, IN_PROGRESS, BLOCKED, DONE), assignee, due date and audit timestamps.
- Rules: status transitions are validated (DONE can't go back to OPEN; BLOCKED needs a reason); due dates can't be in the past on creation.
- Must include: DTO records with Bean Validation, a global Problem Details handler, `@Version` optimistic locking with 409 on conflicts, Flyway migrations, `@WebMvcTest` tests for validation and errors, and one `@SpringBootTest` with Testcontainers PostgreSQL for the happy path.

### Project 4.B: Blog API with pagination and sorting

- Authors, posts, tags (many-to-many through a join table), comments.
- `GET /posts?page=0&size=20&sort=publishedAt,desc&tag=fintech`: paged, sorted and filtered, returning a page envelope with `content`, `page`, `size`, `totalElements`.
- No N+1 queries: the list endpoint must run a fixed number of SQL statements whatever the page size. Prove it with a test that counts statements (Hibernate statistics) and explain your fetch strategy in the README.
- Allowed sort fields are validated against an allow-list (topic 3.5).

### Project 4.C: Product catalogue API with search and filters

A catalogue of banking products (savings, current, fixed deposit, loan products) with rates, fees, eligibility rules and channel availability.

- Search by text (name/description), filter by product type, currency, minimum balance range, channel and active flag; combine any filters.
- Implement filtering with Spring Data JPA **Specifications** or a JPQL query built safely from optional parameters; never by concatenating input into SQL.
- Cache the "active products" list in Redis with a TTL and evict on update (topic 3.6), and expose a cache hit/miss metric (topic 4.6).
- Externalise rate-change approval limits with `@ConfigurationProperties` (topic 4.5).

### Project 4.D: Spring Boot 3 → 4 upgrade exercise

Upgrade the provided Spring Boot 3.5 service in [`labs/04-boot3-to-4-upgrade`](../../../labs/04-boot3-to-4-upgrade/README.md) to Spring Boot 4.1.

1. Run its tests on Boot 3.5 and record the baseline.
2. Add safety nets first: a JSON contract test for the account response, and a test that asserts Flyway applied every migration.
3. Upgrade by hand following topic 4.7: parent version, modular starters (`webmvc`, `flyway`), test starters, `@MockBean` → `@MockitoBean`, `@AutoConfigureMockMvc`, Jackson customiser, and renamed properties (use the properties migrator, then remove it).
4. Get every test green, start the app, and check `/actuator/health/readiness` and the Flyway history table.
5. Write `UPGRADE.md`: every change you made, why, and anything you'd check before doing this on a production service.

Stretch: run the OpenRewrite Boot 4 recipe on a fresh copy and compare its diff with yours.

## How these projects are assessed

| Criterion | Not yet | Competent | Strong |
| --- | --- | --- | --- |
| API design | Inconsistent paths and status codes | Resource-oriented paths, correct status codes, DTOs, Problem Details | Errors carry stable `type` URIs and useful context |
| Persistence | `ddl-auto=update`, entities returned from controllers | Flyway + `validate`, LAZY associations, projections for reads | Proven absence of N+1, correct locking strategy |
| Configuration and security hygiene | Secrets in config | Validated `@ConfigurationProperties`, secrets from the environment | Gitleaks/secret scanning in CI, documented env vars |
| Testing | None, or only happy paths | Web slice tests for validation and errors, one Testcontainers integration test | Edge cases, concurrency and query-count tests |
| Operability | No Actuator | Health, readiness and Prometheus metrics exposed safely | Business metrics and a small Grafana dashboard |
