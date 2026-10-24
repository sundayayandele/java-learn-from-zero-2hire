## What you will be able to do

- Explain why integration tests against **real** PostgreSQL, Redis and Kafka beat in-memory substitutes such as H2.
- Write integration tests with **Testcontainers 2** and Spring Boot's **`@ServiceConnection`**.
- Share containers across test classes so the suite stays fast, and keep tests independent.
- Test transactions, locking, migrations, caching and messaging the way they behave in production.
- Separate fast unit tests from integration tests in Maven (Surefire and Failsafe).

## Why this matters in a bank or fintech

The bugs that cost banks money rarely live in pure functions. They live where code meets infrastructure: a Flyway migration that works on H2 but not on PostgreSQL, a `SELECT ... FOR UPDATE` that does not lock what you think, a deadlock under concurrent transfers, a JSONB query, a Redis key that never expires, a Kafka consumer that commits offsets before processing. Testcontainers lets you catch these on your laptop and in CI, with the same database version as production.

## 1. Why not H2?

H2 in "PostgreSQL mode" is not PostgreSQL. It differs in SQL functions, `ON CONFLICT`, JSONB, window functions, locking, isolation behaviour, sequences, and Flyway migrations written for PostgreSQL. Tests that pass on H2 give false confidence. Testcontainers starts a real PostgreSQL 17 in Docker for your tests in a few seconds.

## 2. Testcontainers with Spring Boot 4

Dependencies (versions managed by the Spring Boot parent; Testcontainers 2 renamed its modules):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Define containers once, in a test configuration:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
    }
}
```

`@ServiceConnection` tells Spring Boot to read the container's host, port and credentials and configure the `DataSource` (or Redis connection) automatically; no `@DynamicPropertySource` needed. Because the containers are beans, Spring starts them once per application context and the context cache reuses them across test classes with the same configuration.

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferIntegrationTest {

    @Autowired TransferService transfers;
    @Autowired JdbcClient jdbc;

    @Test
    void transferMovesMoneyAndBalancesTheJournal() {
        transfers.transfer(new TransferCommand("0123456789", "9876543210", Money.ngn("2500.00"), "it-ref-1"));

        BigDecimal journalSum = jdbc.sql("SELECT SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) FROM posting WHERE journal_ref = 'it-ref-1'")
                .query(BigDecimal.class).single();
        assertThat(journalSum).isEqualByComparingTo("0.00");
    }
}
```

Flyway migrations run at startup against the container, so every integration test also proves your migrations work on real PostgreSQL.

You can also run the application locally with the same containers: a `TestApplication` class in `src/test/java` that calls `SpringApplication.from(Application::main).with(TestcontainersConfiguration.class).run(args)` gives every developer a working database without installing PostgreSQL.

## 3. Keeping tests independent

A shared container is fast, but tests must not depend on each other's data:

- Use **unique references** per test (`"it-ref-" + UUID.randomUUID()`), so tests do not collide.
- Or clean tables before each test (`TRUNCATE ... RESTART IDENTITY CASCADE` in a `@BeforeEach`).
- `@Transactional` on a test class rolls back after each test, which is convenient, **but** it hides bugs: code that relies on commits (locking across transactions, `REQUIRES_NEW`, events after commit, other threads) behaves differently. Do not use it for concurrency or transaction-boundary tests.

## 4. Testing what only a real database can show

### Concurrency and locking

```java
@Test
void fiftyConcurrentTransfersNeverOverdraw() throws Exception {
    seedAccounts(10, "1000.00");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<?>> futures = IntStream.range(0, 50)
                .mapToObj(i -> executor.submit(() -> safeTransfer(randomPair(), "150.00", "c-" + i)))
                .toList();
        for (Future<?> f : futures) f.get();
    }
    assertThat(totalBalance()).isEqualByComparingTo("10000.00");          // money is conserved
    assertThat(minBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);     // nobody overdrawn
}
```

This test would pass on an in-memory map with a global lock and could fail on a real database with a wrong lock order. That is exactly why it belongs here.

### Constraints and migrations

Insert a posting with a negative amount and assert that the `CHECK` constraint rejects it; run a migration test that applies all migrations to an empty database and checks the schema version.

## 5. Redis and Kafka

**Redis** (caching from Phase 1 topic 3.6):

```java
@Test
void ratesAreCachedAndEvictedAfterImport() {
    rateService.rate("USD", "NGN", today);           // miss → database
    rateService.rate("USD", "NGN", today);           // hit → Redis
    assertThat(cacheMisses()).isEqualTo(1);
    importer.importDaily();                          // @CacheEvict
    rateService.rate("USD", "NGN", today);
    assertThat(cacheMisses()).isEqualTo(2);
}
```

**Kafka** (introduced properly in Phase 3; useful here for event publishing):

```java
@Bean
@ServiceConnection
KafkaContainer kafka() {
    return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.4.0"));
}
```

Asynchronous effects need waiting, not sleeping. **Awaitility** polls until a condition holds or a timeout expires:

```java
await().atMost(Duration.ofSeconds(10))
       .untilAsserted(() -> assertThat(testListener.received()).extracting(TransferCompleted::reference).contains("it-ref-7"));
```

## 6. Maven: Surefire and Failsafe

Keep `mvn test` fast for developers and run integration tests in `mvn verify`:

- **Surefire** runs `*Test` classes in the `test` phase (unit tests).
- **Failsafe** runs `*IT` classes in the `integration-test` phase and checks results in `verify`.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution>
    </executions>
</plugin>
```

Rename integration tests to `TransferIT`, `RateCacheIT`. CI runs `./mvnw verify`, which runs both. (GitHub-hosted Linux runners have Docker installed, so Testcontainers works there without extra setup.)

### Speed tips

- One shared configuration (same beans) for all integration tests, so Spring caches one context.
- Alpine or slim images; pre-pull images in CI.
- Reusable containers (`testcontainers.reuse.enable=true` in `~/.testcontainers.properties`) for local runs only.
- Do not mix many different `@MockitoBean` combinations across tests; each combination creates a new context.

## 7. Test slices and the context cache

A full `@SpringBootTest` starts everything: web layer, security, every repository, every client. **Test slices** start only the part you are testing, so they are faster and fail for one reason:

| Annotation | Starts | Use for |
| --- | --- | --- |
| `@WebMvcTest(TransferController.class)` | MVC infrastructure, the controller, `@ControllerAdvice`, security filters, Jackson | Validation, status codes, Problem Details, authorisation rules (topic 7.3) |
| `@DataJpaTest` | JPA, repositories, Flyway, a transaction rolled back after each test | Repository queries, mappings, constraints |
| `@JdbcTest` | `JdbcClient`, `DataSource`, Flyway | SQL written with `JdbcClient` |
| `@JsonTest` | Jackson with your customisations | Money serialisation, date formats, unknown fields |
| `@RestClientTest(ProviderClient.class)` | `RestClient.Builder` with a mock server | Outbound client mapping (or use WireMock) |

`@DataJpaTest` and `@JdbcTest` replace your `DataSource` with an embedded database by default. Keep them on real PostgreSQL by importing your Testcontainers configuration and turning replacement off:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class JournalRepositoryTest { ... }
```

Spring caches each application context and reuses it for every test class with **exactly the same configuration**. Each different combination of `@MockitoBean` fields, `@TestPropertySource` values, profiles or imported configuration creates a **new context** (and starts new containers if they are context beans). To keep suites fast:

- Put shared `@MockitoBean`s and properties on a common base class or a composed annotation, rather than slightly different sets in every class.
- Avoid `@DirtiesContext`; it throws the cached context away. Clean data instead (truncate tables, unique keys per test).
- Use `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`, the replacement for Boot's removed `@MockBean`) only for collaborators you genuinely can't run, such as an external provider; prefer WireMock for HTTP dependencies so the real client code is tested too.
- Watch the log line `Starting ... using Java` during a test run: if it appears many times, you have many contexts.

## Common mistakes

- H2 in tests, PostgreSQL in production.
- `@Transactional` on concurrency tests, so nothing is ever committed or locked across threads.
- Tests depending on data left by earlier tests.
- `Thread.sleep` for asynchronous assertions.
- Integration tests in the unit-test phase, making every local build slow.
- A different container image version from production.
- A new Spring context per test class because of scattered `@MockitoBean`s.

## Practice

1. Replace any H2 tests in your capstone with Testcontainers PostgreSQL using `@ServiceConnection` and a shared configuration.
2. Write the 50-concurrent-transfers test; break the lock ordering deliberately and watch it detect the deadlock or lost update.
3. Add Redis to the test configuration and test cache hits, misses and eviction.
4. Split tests into `*Test` (Surefire) and `*IT` (Failsafe) and measure `mvn test` versus `mvn verify` times.
5. Convert three `@SpringBootTest` classes that only test a repository or a controller into `@DataJpaTest`/`@JdbcTest` and `@WebMvcTest` slices on real PostgreSQL, and compare run times.
6. Count how many application contexts your suite starts, then reduce the number by unifying `@MockitoBean` sets and properties.

## Key terms

Integration test, Testcontainers, `PostgreSQLContainer`, `GenericContainer`, `KafkaContainer`, `@ServiceConnection`, `@TestConfiguration`, context caching, `@Import`, test isolation, `@Transactional` rollback, Awaitility, Surefire, Failsafe, `*IT`, reusable containers, Docker image tag pinning, test slice, `@DataJpaTest`, `@JdbcTest`, `@JsonTest`, `@AutoConfigureTestDatabase`, context cache, `@MockitoBean`, `@DirtiesContext`.

## Further reading

- [Testcontainers for Java documentation](https://java.testcontainers.org/)
- [Spring Boot reference: Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Testcontainers guide: Testing a Spring Boot REST API with Testcontainers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/)
- [Awaitility documentation](https://github.com/awaitility/awaitility/wiki/Usage)
- [Maven Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/)
- [Martin Fowler: The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html)
