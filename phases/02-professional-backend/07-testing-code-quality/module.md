## What this module gives you

Two weeks to turn "it works on my machine" into "I can prove it works, and keep proving it": unit tests and test-driven development with JUnit 5, AssertJ and Mockito (week 20), integration tests against real PostgreSQL, Redis and Kafka with Testcontainers (week 20), API tests with MockMvc, `RestTestClient` and REST Assured (week 21), coverage and mutation testing plus SonarQube quality gates (week 21), and how professional teams review pull requests (week 21). It builds on your first JUnit 5 tests from Phase 1 topic 1.6.

From this module on, every repository you push must have a **test pyramid** (many fast unit tests, fewer integration tests, a handful of end-to-end API tests) and a CI job that fails when coverage or static-analysis thresholds drop.

**Exit check for Module 7:** all five topic CBTs attempted (aim for 70%+); project 7.A or 7.B completed; quality gate configured on at least one capstone.

## Module projects

### Project 7.A: Full test suite for the wallet application

Take your Phase 1 wallet (project 1.A) or ledger (Capstone 1) and give it a professional test suite.

- Unit tests for every business rule, written with AssertJ and parameterised tests for boundaries; Mockito only at real boundaries (repositories, clocks, external clients).
- Testcontainers PostgreSQL integration tests for repositories and the transfer flow, using `@ServiceConnection` and a shared container.
- API tests with `MockMvcTester` for validation and errors, and a REST Assured smoke test against the running application.
- JaCoCo with a minimum of 80% line and 70% branch coverage enforced in `mvn verify`, and a PIT mutation-testing report with your mutation score in the README.

### Project 7.B: TDD kata, tax and fee engine

Build a fee and tax engine **test-first**, committing after every red, green and refactor step so the history shows the cycle.

- Rules: tiered transfer fees, VAT on fees (7.5%), a stamp duty on transfers of ₦10,000 and above, waivers for staff accounts, and a monthly cap. Keep the rates in configuration.
- At least 25 tests, all written before the code that passes them. Explain two design decisions the tests pushed you towards.

### Project 7.C: Integration tests for the e-commerce API

- Use your Phase 1 e-commerce schema (project 3.A) behind a Spring Boot API with orders, payments and stock reservation.
- Integration tests with PostgreSQL and Redis (stock reservation cache) in Testcontainers, plus a Kafka container for `OrderPlaced` events (consumed by a test listener).
- One contract test for the payments client (Spring Cloud Contract or a WireMock-recorded contract) and a SonarQube (or SonarQube Cloud) analysis with the quality gate passing.

### Lab 7.D: Make a flaky test suite trustworthy

A broken-service lab: [Make a flaky test suite trustworthy](../../../labs/07-flaky-test-suite/README.md), a suite that passes locally and fails on a simulated hostile CI runner (time zone, locale, order, parallelism, slowness). Do it alongside the module; it counts as practice, not as the module project, but mentors review it if you submit it with your project.

## How these projects are assessed

| Criterion | Not yet | Competent | Strong |
| --- | --- | --- | --- |
| Test design | Tests mirror the implementation | Tests describe behaviour, clear names, one reason to fail | Parameterised boundaries, test data builders, no test interdependence |
| Test pyramid | Only unit or only end-to-end | Unit, integration (Testcontainers) and API layers present | Right balance with a fast default build and a full build in CI |
| Coverage and mutation | No measurement | JaCoCo thresholds enforced in the build | Mutation score reported and weak tests improved |
| Static analysis | None | SonarQube (or equivalent) runs with a quality gate | Gate blocks merges; findings triaged with justifications |
| Review readiness | Large unexplained commits | Small commits, clear PR description with test evidence | Reviewer checklist used; review comments addressed in follow-up commits |
