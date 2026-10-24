# Spring Boot 4 secure service starter

The baseline every Phase 2 capstone starts from: the [Phase 1 service starter](../spring-boot-service-starter/README.md) plus security, API documentation and CI quality gates. Built and tested on Java 21 + Spring Boot 4.1.2 + Spring Security 7.1.

| Included | Why (topic) |
| --- | --- |
| Everything in the Phase 1 starter: webmvc, validation, data-jpa, Flyway, Actuator, Problem Details, Testcontainers, Dockerfile | Phase 1 |
| Stateless OAuth2 resource server: JWTs validated against the issuer, audience `itl-api` checked | 6.1, 6.2 |
| Authorities from scopes (`SCOPE_api.read`) and Keycloak realm roles (`ROLE_CUSTOMER`, `ROLE_OPS`, `ROLE_ADMIN`); method security enabled | 6.3 |
| `CurrentUser.subject()` for ownership checks | 6.3, 6.4 |
| Health and OpenAPI docs public; other Actuator endpoints need `OPS` | 6.1, 5.2 |
| springdoc OpenAPI 3.1: `/v3/api-docs`, Swagger UI at `/swagger-ui.html` | 5.2 |
| Keycloak 26.8 in Compose with the `itl` realm imported from `keycloak/itl-realm.json`; `scripts/dev-users.sh` creates local test users | 6.2 |
| JaCoCo coverage gate (70% lines; the build fails below it) | 7.4 |
| CycloneDX SBOM in every jar (`META-INF/sbom/application.cdx.json`) | 6.5 |
| CI quality gates: tests + coverage, Trivy dependency and secret scan, dependency review on pull requests, image scan before publishing | 8.3 |
| Six CI/CD platforms: GitHub Actions, GitLab CI, Azure DevOps, AWS CodeBuild, Jenkins, Kubernetes + Argo CD | 8.3, 8.4, 8.5 |

## Use it

1. Copy this folder (or use your capstone's `starter/`, which is generated from it), then rename the package `ng.itlearning.starter` and the `artifactId`.
2. `cp .env.example .env` and set `DB_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`.
3. `docker compose up --build`, wait for Keycloak (<http://localhost:8081>), then create test users: `DEV_USER_PASSWORD=<pick one> KEYCLOAK_ADMIN_PASSWORD=<same as .env> ./scripts/dev-users.sh`.
4. Get a token and call the API:

   ```bash
   TOKEN=$(curl -s -d client_id=itl-cli -d grant_type=password -d username=alice -d password="$DEV_USER_PASSWORD" \
     -d scope='openid api.read api.write' http://localhost:8081/realms/itl/protocol/openid-connect/token | jq -r .access_token)
   curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/...
   ```

   The password grant is enabled on the `itl-cli` client **for local testing only**; real clients use the authorisation code flow with PKCE (browser and mobile apps) or client credentials (services). See topic 6.2.
5. Run the tests (Docker must be running for Testcontainers): `./mvnw verify`. Tests don't need Keycloak: they use `spring-security-test`'s `jwt()` post-processor.

## CI

- **GitHub (primary):** `.github/workflows/ci.yml` runs `./mvnw verify` (tests and coverage gate), a Trivy scan of dependencies and committed secrets, dependency review on pull requests, and on `main` builds the image, scans it, and only then publishes it to GHCR.
- **GitLab (secondary):** `.gitlab-ci.yml` runs the same tests, coverage gate and Trivy scan on gitlab.com's shared runners.
- **Others:** Azure DevOps, AWS CodeBuild, Jenkins and Argo CD files are included; see the [Phase 2 CI/CD guide](../../phases/02-professional-backend/cicd/README.md).

Deployment to a VPS, AWS, Azure and GCP, including the identity provider: [Phase 2 deployment guide](../../phases/02-professional-backend/deploy/README.md).
