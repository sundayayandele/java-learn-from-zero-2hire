## What you will be able to do

- Configure a Spring Boot application with `application.properties`/`application.yaml`, environment variables and command-line arguments, and predict which value wins.
- Bind configuration to type-safe, validated `@ConfigurationProperties` records.
- Use profiles for environment-specific settings without turning them into a maze.
- Keep secrets out of source code and images, load them from the environment or a secrets manager, and rotate them.
- Follow the twelve-factor approach to configuration.

## Why this matters in a bank or fintech

The same build of a payment service runs on a developer laptop, in CI, in UAT and in production, against different databases, partner sandboxes and live endpoints. Mixing those up is how test transactions hit a live switch, or production customers get SMSes from a UAT environment. And a database password or a partner API key committed to Git is a reportable security incident in most banks, often found within minutes by automated scanners on public repositories. Configuration discipline is a core operational skill.

## 1. Where configuration comes from

Spring Boot's `Environment` merges many **property sources**. Later sources in this (simplified) list **override** earlier ones:

1. Defaults in your code (`@ConfigurationProperties` default values)
2. `application.properties` / `application.yaml` inside the jar (`src/main/resources`)
3. Profile-specific files inside the jar: `application-{profile}.properties`
4. `application.properties` and profile files **outside** the jar (in `./config/` or next to it)
5. **OS environment variables**
6. Java system properties (`-Dserver.port=9090`)
7. **Command-line arguments** (`--server.port=9090`)
8. Test-specific properties (`@SpringBootTest(properties = ...)`, `@DynamicPropertySource`)

So you ship sensible defaults in the jar and override per environment with environment variables, which is how Docker, Kubernetes and every PaaS pass configuration.

### Relaxed binding

Environment variables can't contain dots or dashes, so Spring maps them:

| Property | Environment variable |
| --- | --- |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| `bank.transfer.daily-limit` | `BANK_TRANSFER_DAILYLIMIT` |
| `bank.partners.nibss.base-url` | `BANK_PARTNERS_NIBSS_BASEURL` |

Rule: uppercase, dots become underscores, dashes are removed.

### YAML or properties?

Both work; pick one per project. YAML is more readable for nested settings, properties files are harder to break with indentation:

```yaml
spring:
  application:
    name: transfer-service
  datasource:
    url: jdbc:postgresql://localhost:5432/bank
    username: bank_app
    password: ${DB_PASSWORD}            # placeholder resolved from the environment
  threads:
    virtual:
      enabled: true

bank:
  transfer:
    daily-limit: 5000000.00
    single-limit: 1000000.00
    fee-cap: 2000.00
  partners:
    nibss:
      base-url: https://sandbox.nibss.example
      timeout: 3s
```

`${DB_PASSWORD}` reads another property or environment variable; `${DB_PASSWORD:devpass}` adds a default. **Don't give secrets defaults** outside local-only profiles: a missing secret should fail startup loudly, not fall back to something guessable.

## 2. Type-safe configuration with `@ConfigurationProperties`

Avoid scattering `@Value("${bank.transfer.daily-limit}")` across classes. Bind a whole group to a validated record:

```java
@ConfigurationProperties(prefix = "bank.transfer")
@Validated
public record TransferProperties(
        @NotNull @DecimalMin("0.01") BigDecimal dailyLimit,
        @NotNull @DecimalMin("0.01") BigDecimal singleLimit,
        @NotNull BigDecimal feeCap,
        @DefaultValue("PT2S") Duration lockTimeout) { }

@ConfigurationProperties(prefix = "bank.partners.nibss")
@Validated
public record NibssProperties(@NotNull URI baseUrl, @NotNull Duration timeout) { }
```

Register them with `@ConfigurationPropertiesScan` on the main class (or `@EnableConfigurationProperties(TransferProperties.class)`), then inject them like any bean:

```java
@Service
public class LimitService {
    private final TransferProperties limits;
    public LimitService(TransferProperties limits) { this.limits = limits; }
}
```

Benefits over `@Value`: one place documents every setting; types are converted for you (`3s` → `Duration`, `10MB` → `DataSize`, strings → `URI`, `BigDecimal`); **validation fails startup** if a value is missing or invalid, instead of failing at 2 a.m. on the first transfer; and with the `spring-boot-configuration-processor` dependency, IntelliJ autocompletes your custom properties.

## 3. Profiles

A **profile** is a named set of configuration. Activate profiles with `spring.profiles.active`, usually via the environment (`SPRING_PROFILES_ACTIVE=prod`).

```text
src/main/resources/
├── application.yaml          # shared defaults
├── application-local.yaml    # developer laptop: local Docker database, debug SQL logging
└── application-test.yaml     # CI-specific overrides (Testcontainers usually makes this small)
```

Beans can be profile-specific too:

```java
@Bean
@Profile("local")
SmsSender loggingSmsSender() { return new LoggingSmsSender(); }     // prints instead of sending

@Bean
@Profile("!local")
SmsSender realSmsSender(SmsProperties props) { return new HttpSmsSender(props); }
```

Profile groups bundle several: `spring.profiles.group.prod=prod-db,prod-observability`.

Good practice:

- Keep **few** profiles, mostly for *behaviour* differences (fake vs real SMS sender). Values that differ per environment (URLs, sizes, credentials) should come from **environment variables**, not from `application-uat.yaml`, `application-prod.yaml`, `application-dr.yaml`... That way the same artefact runs anywhere and production values never live in the jar.
- **Never** let a profile switch on dangerous behaviour silently (for example, `@Profile("local")` disabling security). Make it loud, and ensure `local` can't be activated in production.

## 4. Secrets

A secret is anything that grants access: database passwords, API keys, OAuth client secrets, private keys, JWT signing keys, webhook signing secrets, encryption keys.

### Rules

1. **Never commit secrets** to Git, not even in a private repository or "temporarily". Git history keeps them forever; removing a file in a later commit does not remove it from history.
2. **Never bake them into Docker images** (`ENV DB_PASSWORD=...` in a Dockerfile is visible to anyone who can pull the image).
3. **Never log them.** Spring Boot's Actuator `env` and `configprops` endpoints sanitise values by default (shown as `******`); keep it that way (topic 4.5).
4. **Least privilege**: the application's database user can read and write its own tables, not drop them or read other schemas.
5. **Rotate** secrets, and immediately if one leaks.

### Where secrets come from, in increasing maturity

| Approach | How | Notes |
| --- | --- | --- |
| Environment variables | `DB_PASSWORD` set by the platform (Docker Compose `env_file`, CI variables, VPS systemd `EnvironmentFile`) | Simple and universal. Fine for a VPS or small team |
| Mounted files | Kubernetes Secrets or Docker secrets mounted as files; Spring reads them with `spring.config.import=configtree:/run/secrets/` | Avoids exposing values in the process environment |
| Secrets manager | AWS Secrets Manager / Parameter Store, Azure Key Vault, GCP Secret Manager, HashiCorp Vault | Central control, audit logs, automatic rotation. Standard in banks |

With `spring.config.import` Spring Boot can load configuration from extra sources at startup:

```yaml
spring:
  config:
    import:
      - optional:file:.env[.properties]          # local developer convenience (git-ignored!)
      - optional:configtree:/run/secrets/         # each file name = property name, content = value
```

Cloud-specific integrations (Spring Cloud AWS, Spring Cloud Azure, Spring Cloud GCP, Spring Cloud Vault) add `spring.config.import=aws-secretsmanager:...`-style sources. Module 12 uses them; the deployment guide for the capstones shows the simplest secure option for each platform.

### Local development without committed secrets

- A `.env` file (or `application-local.yaml` with local Docker credentials) listed in `.gitignore`.
- A committed `.env.example` with **placeholder** values that documents what's needed.
- A pre-commit or CI secret scanner (Gitleaks, GitHub secret scanning and push protection) to catch mistakes.

```text
# .env.example: copy to .env and fill in. Never commit .env.
DB_PASSWORD=change-me
NIBSS_CLIENT_SECRET=change-me
```

### If a secret leaks

Treat it as an incident: **rotate first** (issue a new credential and revoke the old one), then clean up history if needed, then find out how it happened. Rewriting Git history alone doesn't help: assume anything pushed to a public repository has been copied.

## 5. The twelve-factor view

The [Twelve-Factor App](https://12factor.net/config) principle III says: store config in the environment, strictly separated from code, so that the codebase could be made open source at any moment without compromising credentials. A practical test for your repository: "If this repo were public tomorrow, would anything need rotating?" The answer must be no.

## Worked example: configuring the transfer service for three environments

```yaml
# application.yaml (in the jar: safe defaults, no secrets)
spring:
  application.name: transfer-service
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
bank:
  transfer:
    daily-limit: ${TRANSFER_DAILY_LIMIT:5000000.00}
    single-limit: ${TRANSFER_SINGLE_LIMIT:1000000.00}
    fee-cap: 2000.00
management:
  endpoints.web.exposure.include: health,info,prometheus
```

```yaml
# application-local.yaml (developer laptop, git-ignored credentials come from .env)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bank
    username: postgres
logging.level.org.hibernate.SQL: debug
```

- **Local**: `SPRING_PROFILES_ACTIVE=local` plus `.env` with the local password.
- **CI**: Testcontainers provides the database; no profile, no secrets.
- **Production** (VPS, AWS, Azure or GCP): the platform injects `DB_URL`, `DB_USER` and `DB_PASSWORD` from its secret store; no profile needed. The jar is byte-for-byte the one that passed CI.

If `DB_PASSWORD` is missing, startup fails immediately with a clear message, which is exactly what you want.

## Common mistakes

- Secrets in `application.yaml`, Dockerfiles, test fixtures or CI scripts.
- `@Value` sprinkled everywhere with no validation.
- Defaults for secrets (`${DB_PASSWORD:admin}`).
- One profile per environment holding production values inside the jar.
- Forgetting relaxed-binding rules and setting `BANK_TRANSFER_DAILY-LIMIT`.
- Logging the whole configuration at startup "for debugging".

## Practice

1. Bind `bank.transfer.*` to a validated `TransferProperties` record. Start the app with a missing or negative limit and read the failure.
2. Override a property four ways (YAML, profile file, environment variable, command-line argument) and prove the precedence order.
3. Create a `local` profile with a `LoggingSmsSender` and a default `HttpSmsSender`, and test both wirings.
4. Install Gitleaks, commit a fake key on a throwaway branch, and watch it get caught. Then add a GitHub Actions secret-scanning step to your project.

## Key terms

Property source, precedence, relaxed binding, placeholder, `@ConfigurationProperties`, `@Validated`, `@ConfigurationPropertiesScan`, `@Value`, profile, `@Profile`, profile group, secret, least privilege, rotation, `.env`, `spring.config.import`, configtree, secrets manager, secret scanning, twelve-factor.

## Further reading

- [Spring Boot reference: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot reference: Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [The Twelve-Factor App: Config](https://12factor.net/config)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [GitHub: About push protection](https://docs.github.com/en/code-security/secret-scanning/introduction/about-push-protection)
- [Spring Boot reference: Common application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
