# Starter: Settlement reconciliation engine

Generated from the [Spring Boot service starter](../../../../../templates/spring-boot-service-starter/README.md) by `scripts/new_starter.py`. Read the [capstone brief](../README.md) first.

## Get going

1. Create a new **public** GitHub repository (for example `settlement-reconciliation-engine`) and copy the contents of this folder into it, including the hidden files (`.github/`, `.gitlab-ci.yml`, `.gitignore`, `.env.example`, `.mvn/`).
2. `cp .env.example .env`, set `DB_PASSWORD`, then `docker compose up --build` and open <http://localhost:8080/actuator/health>.
3. `./mvnw verify` runs the tests (Docker must be running for Testcontainers).
4. Push to GitHub: the Actions workflow runs on every push. Optionally mirror to gitlab.com for the GitLab pipeline.

## What's already here

- `ReconApplication`: the Spring Boot 4.1 application, with an injectable `Clock`.
- `web/ApiExceptionHandler`: RFC 9457 Problem Details for validation errors and unexpected errors. Add a handler per domain exception.
- `db/migration/V1__baseline.sql`: replace it with your first real migration.
- Integration tests with a real PostgreSQL (Testcontainers) proving migrations ran and readiness is UP.
- Dockerfile and Compose file.
- CI/CD for six platforms: GitHub Actions (primary, `.github/workflows/ci.yml`), GitLab CI (`.gitlab-ci.yml`), Azure DevOps (`azure-pipelines.yml`), AWS CodeBuild/CodePipeline (`buildspec.yml`), Jenkins (`Jenkinsfile`), and Kubernetes manifests with an Argo CD GitOps Application (`deploy/`). See the [CI/CD guide](../../../cicd/README.md).

## Your first tasks

- [ ] Replace `V1__baseline.sql` with your schema (topics 3.1, 3.4) and add constraints for every rule the database can enforce.
- [ ] Model the domain in plain Java first, with unit tests (records, sealed types, `BigDecimal`).
- [ ] Add your `@ConfigurationProperties` record for thresholds, limits and URLs (topic 4.5).
- [ ] Build the API with DTO records, validation and a Problem Detail per domain exception (topic 4.3).
- [ ] Add at least two business metrics (topic 4.6).
- [ ] Write the acceptance tests listed in the brief, then make them pass.
- [ ] Deploy with the [deployment guide](../../../deploy/README.md) and put the health URL in your README.
- [ ] Copy the data source attribution from the brief into your README.
