# Spring Boot 4 service starter

The baseline every Phase 1 Spring Boot project and capstone starts from. Built and tested on Java 21 + Spring Boot 4.1.2.

| Included | Why (topic) |
| --- | --- |
| Spring Boot 4.1 modular starters: webmvc, validation, data-jpa, flyway, actuator | 4.1, 4.6 |
| Flyway migrations with `ddl-auto=validate` | 3.4, 4.3 |
| RFC 9457 Problem Details error handler | 4.2 |
| Configuration from environment variables, no secrets committed (`.env.example`) | 4.4 |
| Actuator health, readiness and Prometheus metrics; `heapdump` disabled | 4.5 |
| Virtual threads enabled | 2.3 |
| Testcontainers PostgreSQL integration tests (`@ServiceConnection`) | 3.x, 4.x |
| Multi-stage, layered, non-root Dockerfile and Docker Compose | Deploy guide |
| GitHub Actions CI (primary) and GitLab CI (secondary) | Deploy guide |

## Use it

1. Copy this folder (or use your capstone's `starter/`, which is generated from it), then rename the package `ng.itlearning.starter` and the `artifactId`.
2. `cp .env.example .env` and set `DB_PASSWORD`.
3. Run everything: `docker compose up --build`, then open <http://localhost:8080/actuator/health>.
4. Or run the database in Docker and the app from IntelliJ: `docker compose up -d postgres`, then run `StarterApplication` with the environment variables `DB_URL=jdbc:postgresql://localhost:5432/app`, `DB_USER=app`, `DB_PASSWORD=...`.
5. Run the tests (Docker must be running for Testcontainers): `./mvnw verify`.

## CI

- **GitHub (primary):** push to GitHub and `.github/workflows/ci.yml` builds and tests every push and pull request, and publishes an image to GitHub Container Registry (`ghcr.io/<you>/<repo>`) from `main`.
- **GitLab (secondary):** create a free account at gitlab.com, create a project, and push the same repository (`git remote add gitlab git@gitlab.com:<you>/<repo>.git && git push gitlab main`). `.gitlab-ci.yml` runs the same tests on gitlab.com's shared runners and publishes to the GitLab Container Registry.

Deployment to a VPS, AWS, Azure and GCP: see the [Phase 1 deployment guide](../../phases/01-foundations/deploy/README.md).
