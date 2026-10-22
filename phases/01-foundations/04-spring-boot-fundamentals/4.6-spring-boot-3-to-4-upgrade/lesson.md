## What you will be able to do

- Explain what Spring Boot 4 and Spring Framework 7 changed, and why teams must move off Spring Boot 3.x.
- Recognise Boot 3 code and configuration that breaks or is deprecated on Boot 4.
- Migrate a Boot 3.5 application to Boot 4.x step by step: dependencies and modular starters, Jackson 3, test changes, removed features and renamed properties.
- Use the properties migrator and OpenRewrite to automate the mechanical parts, and verify the result.

## Why this matters in a bank or fintech

The ITLearning programme notes it plainly: Spring Boot 3.x reached the end of open-source support in mid-2026, and new projects start on Spring Boot 4. Banks cannot run unsupported frameworks in production for long, so through 2026 and 2027 almost every Spring team has a "Boot 4 upgrade" on its roadmap. Engineers who have done one, and can explain the trade-offs, are exactly who those teams want. Interviewers ask "what changed in Spring Boot 4?" as a quick check of whether a candidate's knowledge is current.

## 1. The big picture

| | Spring Boot 3.x | Spring Boot 4.x |
| --- | --- | --- |
| Released | Nov 2022 (3.0) – 2025 (3.5) | Nov 2025 (4.0), 4.1 in 2026 |
| Spring Framework | 6.x | **7.x** |
| Jakarta EE | 9/10 | **11** (Servlet 6.1, Persistence 3.2, Validation 3.1) |
| Minimum Java | 17 | 17 (Java 21 or 25 recommended) |
| JSON | Jackson 2 (`com.fasterxml.jackson`) | **Jackson 3** (`tools.jackson`) |
| Hibernate | 6.x | **7.x** |
| Starters | Few large starters | **Modular**: one starter (and one test starter) per technology |
| Null safety | `org.springframework.lang` annotations | **JSpecify** annotations across the portfolio |
| Kotlin | 1.7+ | 2.2+ |
| Embedded servers | Tomcat, Jetty, Undertow | Tomcat, Jetty (**Undertow removed**) |
| Testing | `@MockBean`, `@SpyBean` | Removed: use `@MockitoBean`, `@MockitoSpyBean`; new `RestTestClient` |

New capabilities you can use straight away:

- **API versioning** built into Spring MVC and WebFlux (`spring.mvc.apiversion.*`, `@GetMapping(path = "/accounts", version = "2")`), covered in Module 5.
- **HTTP service clients**: declare a Java interface with `@HttpExchange` methods and Spring generates the client; Boot 4 auto-configures them.
- **Resilience in the core framework**: `@Retryable` and `@ConcurrencyLimit` in Spring Framework 7 (enable with `@EnableResilientMethods`), so simple retries no longer need Spring Retry.
- **OpenTelemetry starter** (`spring-boot-starter-opentelemetry`) for metrics and traces export (Module 13).
- In **4.1**: Spring gRPC support, more observability conventions, HTTP client SSRF protection with `InetAddressFilter`, and more.

## 2. Modular starters: the change you'll hit first

Spring Boot 4 split its large `spring-boot-autoconfigure` jar into focused modules (`spring-boot-webmvc`, `spring-boot-jdbc`, `spring-boot-flyway`, ...), each with a starter and a test starter.

**What breaks:**

1. **Technologies that used to work with just the library now need their starter.** The classic example is Flyway and Liquibase:

   ```xml
   <!-- Boot 3: the library alone was enough -->
   <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>

   <!-- Boot 4: use the starter, or migrations silently stop running at startup -->
   <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
   <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
   ```

   That "silently" is why you must test the upgrade properly: the app starts, but the schema isn't migrated.

2. **Renamed starters** (old names still work but are deprecated):

   | Boot 3 | Boot 4 |
   | --- | --- |
   | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
   | `spring-boot-starter-web-services` | `spring-boot-starter-webservices` |
   | `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
   | `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
   | `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` |

3. **Test starters per technology.** Instead of one `spring-boot-starter-test`, add the test starter for each technology you test: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test` and so on (each brings `spring-boot-starter-test` transitively). Some test annotations moved with them; for example `@WithMockUser` needs `spring-boot-starter-security-test`.

4. **Package moves.** Auto-configuration classes and some APIs moved to `org.springframework.boot.<module>` packages. Application code rarely imports them, but custom auto-configurations, test slices and `TestRestTemplate` users will see import errors (`org.springframework.boot.resttestclient.TestRestTemplate`).

**Shortcut for large apps:** the **classic starters** (`spring-boot-starter-classic`, `spring-boot-starter-test-classic`) bring back the "everything on the classpath" behaviour of Boot 3 so you can upgrade in two steps. The Spring team recommends moving off them afterwards.

## 3. Jackson 3

Spring Boot 4 prefers Jackson 3:

- Group IDs and packages change from `com.fasterxml.jackson` to **`tools.jackson`** (for example `tools.jackson.databind.json.JsonMapper`). The **annotations** (`@JsonProperty`, `@JsonIgnore`, ...) stay in `com.fasterxml.jackson.annotation`, so most DTOs compile unchanged.
- Boot auto-configures a `JsonMapper` (and an `XmlMapper` if XML is present). To customise, define a `JsonMapperBuilderCustomizer` (formerly `Jackson2ObjectMapperBuilderCustomizer`) or a `JsonMapper` bean; defining an `ObjectMapper` bean is no longer enough to replace it.
- `@JsonComponent` became `@JacksonComponent`; `@JsonMixin` became `@JacksonMixin`.
- Properties moved: `spring.jackson.read.*`/`write.*` became `spring.jackson.json.read.*`/`write.*`.
- Jackson 3 registers every module found on the classpath and has different defaults in places. `spring.jackson.use-jackson2-defaults=true` keeps Boot 3-like behaviour while you migrate.
- If a library still needs Jackson 2, a deprecated `spring-boot-jackson2` module exists as a stop-gap.

Write a **JSON contract test** for every public response before upgrading (serialise a known object, compare with a stored JSON file), so any change in dates, nulls, enums or number formatting shows up immediately.

## 4. Other changes to check

**Testing**

- `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean` (Spring Framework). They are not 100% identical: for example they can't be declared on `@Configuration` classes; put them on test classes.
- `@SpringBootTest` no longer configures MockMvc automatically: add `@AutoConfigureMockMvc`. It no longer provides `TestRestTemplate` or `WebTestClient` automatically either: add `@AutoConfigureTestRestTemplate`/`@AutoConfigureRestTestClient`, or switch to the new `RestTestClient`.
- If `@Mock`/`@Captor` fields stopped working, use Mockito's own `MockitoExtension` (the Boot listener that used to handle them was removed).
- Testcontainers 2.0: module artifact names changed (`org.testcontainers:testcontainers-postgresql`).

**Web and data**

- Undertow is gone: switch to Tomcat (default) or Jetty.
- `HttpMessageConverters` is deprecated in favour of the framework's converter configuration.
- Hibernate 7 / Jakarta Persistence 3.2: review deprecated APIs and any Hibernate-specific annotations; `hibernate-jpamodelgen` became `hibernate-processor`.
- MongoDB: changed default representations for `UUID` and `BigDecimal`. Configure them explicitly before upgrading a database with existing data.
- Property renames, for example `spring.dao.exceptiontranslation.enabled` → `spring.persistence.exceptiontranslation.enabled` and `management.tracing.enabled` → `management.tracing.export.enabled`.

**Actuator**

- Liveness and readiness probes are on by default (topic 4.5).
- Null-safety annotations moved to JSpecify; `javax.annotation.NonNull` and `org.springframework.lang.Nullable` aren't supported on endpoint parameters any more.

**Removed**: Undertow, Pulsar Reactive, embedded launch scripts for "fully executable" jars, Spring Session for Hazelcast and MongoDB (now community projects), and Spock integration in 4.0 (restored in 4.1). Everything deprecated in 3.x was removed.

**Dependencies you manage yourself** (not by Boot), such as Spring Cloud, springdoc-openapi, Resilience4j, MapStruct and Lombok, need versions that support Boot 4. Check each project's compatibility table *before* you start.

## 5. A migration plan

1. **Upgrade to the latest Boot 3.5.x first**, fix every deprecation warning (`-Xlint:deprecation`), and get a green build. Deprecated APIs in 3.x are removed in 4.0.
2. **Check third-party compatibility**: Spring Cloud release train, springdoc, Testcontainers, Lombok/MapStruct, internal shared libraries.
3. **Add safety nets**: JSON contract tests, a test that asserts Flyway migrations ran (e.g. checks `flyway_schema_history`), Actuator smoke tests, and integration tests against a real PostgreSQL via Testcontainers.
4. **Run OpenRewrite** on a branch:

   ```bash
   ./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
     -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE \
     -Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0
   ```

   (Check the OpenRewrite docs for the current recipe name; it handles versions, starters, many imports and properties.) Review the entire diff.
5. **Add the properties migrator temporarily** to find renamed or removed properties at startup:

   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-properties-migrator</artifactId>
     <scope>runtime</scope>
   </dependency>
   ```

   It logs every property that changed and temporarily maps it for you. Fix them in your config files, then **remove the dependency**.
6. **Fix compilation** (packages, removed APIs), then **tests** (test starters, `@MockitoBean`, `@AutoConfigureMockMvc`), then **runtime** (start the app, check `/actuator/health`, `/actuator/conditions`, Flyway history, JSON output).
7. **Upgrade to the latest 4.x maintenance release** (4.1.x at the time of writing), and run the full suite again.
8. **Deploy to a test environment, compare behaviour**, and roll out gradually with the previous version ready.

## Worked example: upgrading a small Boot 3.5 service

Before (`pom.xml` excerpt):

```xml
<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.7</version></parent>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

```java
@SpringBootTest
class TransferApiTest {
    @Autowired MockMvc mvc;                        // relies on MockMvc support that Boot 4's @SpringBootTest no longer adds
    @MockBean FraudClient fraud;                   // removed in Boot 4
}

@Configuration
class JsonConfig {
    @Bean Jackson2ObjectMapperBuilderCustomizer money() {
        return b -> b.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
```

After:

```xml
<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.2</version></parent>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

```java
@SpringBootTest
@AutoConfigureMockMvc
class TransferApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean FraudClient fraud;
}

@Configuration
class JsonConfig {
    @Bean JsonMapperBuilderCustomizer money() {
        return b -> b.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);   // tools.jackson.databind.DeserializationFeature
    }
}
```

Then: run the properties migrator, start the app, confirm `flyway_schema_history` advanced, compare JSON contract snapshots, and check `/actuator/health/readiness`. The project for this module (4.D) asks you to do exactly this on a provided Boot 3.5 application.

## Common mistakes

- Jumping straight from an old 3.x to 4.x without passing through 3.5 and fixing deprecations.
- Forgetting the Flyway/Liquibase starter, so migrations stop running with no error.
- Leaving the properties migrator in production.
- Assuming JSON output is unchanged after the Jackson 3 switch.
- Upgrading Boot without checking Spring Cloud and other third-party versions.
- Staying on the classic starters indefinitely.

## Practice

1. Read the official migration guide end to end and write a one-page checklist for your team.
2. Take a Spring Boot 3.5 sample (the module project 4.D repository, or generate one at start.spring.io with Boot 3.5), add a JSON contract test and a Flyway assertion test, then upgrade it to 4.1 manually. Record every error and its fix.
3. Repeat on a fresh copy using OpenRewrite. Compare the automated diff with your manual one.
4. Add the properties migrator, list every property it reports, and fix them.

## Key terms

Spring Framework 7, Jakarta EE 11, modular starters, test starters, classic starters, Jackson 3, `tools.jackson`, `JsonMapper`, `JsonMapperBuilderCustomizer`, JSpecify, `@MockitoBean`, `RestTestClient`, `@AutoConfigureMockMvc`, properties migrator, OpenRewrite recipe, API versioning, HTTP service client, `@Retryable`/`@ConcurrencyLimit`.

## Further reading

- [Spring Boot 4.0 Migration Guide (official wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Spring Framework 7: What's New](https://github.com/spring-projects/spring-framework/wiki/What%27s-New-in-Spring-Framework-7.x)
- [OpenRewrite: Spring Boot migration recipes](https://docs.openrewrite.org/recipes/java/spring)
