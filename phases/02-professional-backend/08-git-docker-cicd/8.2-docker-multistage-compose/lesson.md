## What you will be able to do

- Explain images, layers, containers, registries and tags, and why containers suit Java services.
- Write a **multi-stage Dockerfile** for Spring Boot that uses the **layered jar**, runs as a **non-root** user on a slim JRE, and builds fast thanks to layer caching.
- Build images without a Dockerfile using **Cloud Native Buildpacks** (`spring-boot:build-image`).
- Size the **JVM inside a container** correctly.
- Run a multi-service stack with **Docker Compose**: health checks, dependencies, volumes, networks and secrets.
- **Scan** images for vulnerabilities and keep them small.

## Why this matters in a bank or fintech

Almost every new banking service ships as a container, whether it runs on Kubernetes, ECS, Azure Container Apps or a single VPS. The image is what passes security review, what is scanned for vulnerabilities, and what is promoted from test to production unchanged. A 1.2 GB image running as root with a full JDK and build tools inside is a finding in any security review; a 200 MB non-root image with a known SBOM is what platform teams expect.

## 1. The model

- An **image** is a read-only template made of **layers** (each Dockerfile instruction that changes files adds one). Layers are cached and shared.
- A **container** is a running instance of an image, with its own writable layer, process namespace and network.
- A **registry** (Docker Hub, GitHub Container Registry, Amazon ECR, Azure Container Registry) stores images by **repository** and **tag** (`ghcr.io/acme/wallet-api:1.4.2`). Tags can move; the **digest** (`@sha256:...`) is immutable.

Build once, run anywhere: the image you tested in CI is exactly the image you deploy.

## 2. A production Dockerfile for Spring Boot

The service starter from Phase 1 already uses this pattern; here is why each line is there.

```dockerfile
# ---------- build stage: has the JDK and Maven, never shipped ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline            # cached until pom.xml changes
COPY src src
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# ---------- runtime stage: JRE only, non-root ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /src/target/extracted/dependencies/ ./
COPY --from=build /src/target/extracted/spring-boot-loader/ ./
COPY --from=build /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build /src/target/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

- **Multi-stage:** the JDK, Maven and source code stay in the build stage; the final image has only the JRE and your application.
- **Dependency caching:** copying `pom.xml` and resolving dependencies before copying `src` means a code-only change does not re-download the internet.
- **Layered jar:** Spring Boot splits the fat jar into `dependencies` (change rarely), `spring-boot-loader`, `snapshot-dependencies` and `application` (changes every build). Copying them in that order means a rebuild and a `docker pull` only transfer the small application layer.
- **Non-root user:** if the application is compromised, the attacker does not get root in the container. Kubernetes platforms often refuse root containers anyway.
- **Tests:** skipped in the image build because CI ran them before; the image build must not be the first place tests run.
- **`.dockerignore`:** exclude `target/`, `.git/`, `.env`, IDE folders, so they never enter the build context (or the image).

Build and run:

```bash
docker build -t wallet-api:dev .
docker run --rm -p 8080:8080 --env-file .env wallet-api:dev
docker image ls wallet-api            # check the size
docker history wallet-api:dev         # see each layer's size
```

### Base image choices

| Base | Size | Notes |
| --- | --- | --- |
| `eclipse-temurin:21-jre` (Ubuntu) | ~90 MB compressed | Good default, glibc, frequent security updates |
| `eclipse-temurin:21-jre-alpine` | Smaller | musl libc; fine for most Spring apps, test first |
| Distroless (`gcr.io/distroless/java21-debian12`) | Small, no shell | Smallest attack surface; harder to debug |
| Custom `jlink` runtime | Smallest | Only the JDK modules you use (Phase 1 topic 2.5) |

Pin a version tag (and ideally the digest) so builds are reproducible, and let Dependabot or Renovate propose base-image updates.

## 3. Buildpacks: no Dockerfile

Spring Boot can build an OCI image with **Cloud Native Buildpacks** (Paketo):

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=ghcr.io/acme/wallet-api:1.4.2
```

You get a layered, non-root image with a JVM memory calculator and an SBOM, and base-image patches can be applied by rebasing the image without rebuilding your code. Many banks prefer buildpacks for consistency across hundreds of services; a Dockerfile gives you more control. Know both.

## 4. The JVM in a container

Since Java 10 the JVM reads the container's memory and CPU limits. Size the heap as a **percentage** of the container limit rather than a fixed `-Xmx`:

- `-XX:MaxRAMPercentage=75` leaves room for metaspace, thread stacks, direct buffers and the code cache (non-heap memory is real; a 100% heap gets the container OOM-killed).
- `-XX:+ExitOnOutOfMemoryError` makes the JVM exit so the orchestrator restarts it, instead of limping on.
- Give each service a realistic limit (512 MB–1 GB for a typical Spring Boot API); measure with Actuator's JVM metrics.
- With virtual threads (Phase 1 topic 2.3), request concurrency no longer means one OS thread per request, but the database pool and memory still bound throughput.

## 5. Docker Compose for a local stack

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: wallet
      POSTGRES_USER: wallet
      POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U wallet -d wallet"]
      interval: 5s
      retries: 10

  redis:
    image: redis:8-alpine
    healthcheck: { test: ["CMD", "redis-cli", "ping"], interval: 5s }

  keycloak:
    image: quay.io/keycloak/keycloak:26.8
    command: start-dev --import-realm
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:?set it in .env}
    volumes: [./keycloak:/opt/keycloak/data/import:ro]
    ports: ["8180:8080"]

  app:
    build: .
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/wallet
      SPRING_DATASOURCE_USERNAME: wallet
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8080/realms/retail
    ports: ["8080:8080"]

volumes:
  pgdata:
```

- Services reach each other by **service name** on Compose's default network (`postgres:5432`), not `localhost`.
- `depends_on` with `condition: service_healthy` waits for the dependency's health check, not just its start.
- **Named volumes** keep data between `docker compose down` and `up` (`down -v` deletes them).
- Secrets come from a git-ignored `.env`; the `:?` syntax fails fast when a variable is missing.
- Only publish ports you need from your laptop; in production, put only a reverse proxy on the public network (Phase 1 deployment guide).

Spring Boot can also start Compose for you in development: add `spring-boot-docker-compose` and Boot runs `docker compose up` on startup and creates connection details automatically.

## 6. Scanning and hardening

- **Scan images** in CI with Trivy (or Grype, Docker Scout) and fail on critical vulnerabilities that have a fix:

```bash
trivy image --severity CRITICAL,HIGH --ignore-unfixed --exit-code 1 ghcr.io/acme/wallet-api:${GIT_SHA}
```

- **SBOMs** for images (`trivy image --format cyclonedx` or the buildpack's built-in SBOM).
- **Read-only root filesystem** where possible (`read_only: true` in Compose, `readOnlyRootFilesystem` in Kubernetes), with a `tmpfs` for `/tmp`.
- Drop Linux capabilities you do not need; never run `--privileged`.
- No secrets in images: not in `ENV`, not in `COPY`ed files, not in build arguments (they appear in `docker history`).
- Tag images with the **commit SHA** (immutable) plus a human-friendly version; deploy by SHA or digest, never by `latest`.

## Common mistakes

- Single-stage images with the JDK, Maven and source code inside.
- Running as root.
- `COPY . .` before resolving dependencies, so every build re-downloads everything.
- No `.dockerignore`, so `.git`, `target` and `.env` end up in the image.
- `-Xmx` equal to the container limit, causing OOM kills.
- Deploying `:latest`.
- Secrets passed as build arguments or baked into layers.
- `depends_on` without health checks, so the app starts before the database accepts connections.

## Practice

1. Build your capstone with the multi-stage Dockerfile, then with `spring-boot:build-image`; compare sizes, layers and start times.
2. Change one line of code and rebuild; confirm with the build output that only the application layer was rebuilt.
3. Write a Compose file for the app, PostgreSQL, Redis and Keycloak with health-based dependencies, and start it on a clean machine with only a `.env` file.
4. Scan the image with Trivy, fix or document every critical finding, and set `read_only: true` with a `tmpfs` for `/tmp`.

## Key terms

Image, layer, container, registry, repository, tag, digest, Dockerfile, build context, `.dockerignore`, multi-stage build, layered jar, `jarmode=tools`, `JarLauncher`, non-root user, base image, distroless, Cloud Native Buildpacks, Paketo, `spring-boot:build-image`, `MaxRAMPercentage`, OOM kill, Docker Compose, service, network, named volume, health check, `depends_on`, `.env`, Trivy, image scanning, read-only root filesystem.

## Further reading

- [Docker documentation: Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Spring Boot reference: Efficient container images (layering)](https://docs.spring.io/spring-boot/reference/packaging/container-images/efficient-images.html)
- [Spring Boot reference: Cloud Native Buildpacks](https://docs.spring.io/spring-boot/reference/packaging/container-images/cloud-native-buildpacks.html)
- [Docker documentation: Compose file reference](https://docs.docker.com/reference/compose-file/)
- [OWASP Cheat Sheet: Docker Security](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html)
- [Trivy documentation](https://trivy.dev/latest/docs/)
