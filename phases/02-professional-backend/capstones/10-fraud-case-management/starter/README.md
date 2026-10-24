# Starter: Fraud case management service

Generated from the [Spring Boot secure service starter](../../../../../templates/spring-boot-secure-service-starter/README.md) by `scripts/new_starter.py`. Read the [capstone brief](../README.md) and the [step-by-step guide](../GUIDE.md) first.

## Get going

1. Create a new **public** GitHub repository (for example `fraud-case-management`) and copy the contents of this folder into it, including the hidden files (`.github/`, `.gitlab-ci.yml`, `.gitignore`, `.env.example`, `.mvn/`).
2. `cp .env.example .env`, set `DB_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`, then `docker compose up --build`.
3. Create local test users: `DEV_USER_PASSWORD=<pick one> KEYCLOAK_ADMIN_PASSWORD=<same as .env> ./scripts/dev-users.sh`, then get a token as shown in the [template README](../../../../../templates/spring-boot-secure-service-starter/README.md#use-it).
4. `./mvnw verify` runs the tests and the coverage gate (Docker must be running for Testcontainers).
5. Push to GitHub: the Actions workflow runs tests, coverage, dependency and secret scans on every push.

## What's already here

- `CasesApplication`: the Spring Boot 4.1 application.
- `security/SecurityConfig`: stateless OAuth2 resource server; scopes become `SCOPE_x`, Keycloak realm roles become `ROLE_x`; method security on.
- `security/CurrentUser`: the caller's subject, for ownership checks.
- `web/ApiExceptionHandler`: RFC 9457 Problem Details, including 403 for method-security failures.
- `keycloak/itl-realm.json`: realm `itl` with roles `CUSTOMER`, `OPS`, `ADMIN`, scopes `api.read`, `api.write`, and audience `itl-api`.
- springdoc OpenAPI at `/v3/api-docs` and `/swagger-ui.html`.
- JaCoCo coverage gate at 70% lines, CycloneDX SBOM, Trivy scans, and CI/CD for six platforms (see the [CI/CD guide](../../../cicd/README.md)).

## Your first tasks

- [ ] Write the OpenAPI contract for the brief's API first (topic 5.2) and review it before coding.
- [ ] Replace `V1__baseline.sql` with your schema and constraints.
- [ ] Add your domain model with unit tests (TDD, topic 7.1).
- [ ] Secure every endpoint with the roles and scopes in the brief, and test 401, 403 and ownership (topics 6.3, 7.3).
- [ ] Add integrations behind interfaces with timeouts, retries and WireMock tests (topics 5.4, 7.2).
- [ ] Keep `./mvnw verify` and every CI gate green on `main`.
- [ ] Deploy with the [deployment guide](../../../deploy/README.md) and put the health URL in your README.
- [ ] Copy the data and sandbox attribution from the brief into your README.
