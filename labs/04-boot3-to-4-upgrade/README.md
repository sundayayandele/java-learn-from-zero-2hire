# Lab 4.D: Upgrade a Spring Boot 3.5 service to Spring Boot 4.1

← [Module 4](../../phases/01-foundations/04-spring-boot-fundamentals/README.md) · [Topic 4.6](../../phases/01-foundations/04-spring-boot-fundamentals/4.6-spring-boot-3-to-4-upgrade/README.md)

`account-service` is a small, working **Spring Boot 3.5.16** service: one entity, Flyway migrations, a REST endpoint, a Jackson customiser, a `@MockBean` test and a Testcontainers integration test. It is typical of what you'll find in real teams.

## Baseline

```bash
./mvnw verify          # green on Boot 3.5 (Docker must be running for Testcontainers)
```

## Your task

1. Record the baseline: tests green, and note what `accountJsonContract` asserts (it's your JSON safety net).
2. Add one more safety net: a test that asserts Flyway applied `V1` (query `flyway_schema_history`).
3. Upgrade to Spring Boot **4.1.x** by hand, following topic 4.6. You should hit, at minimum:
   - the parent version and Testcontainers 2 artifact/package changes;
   - modular starters (`webmvc`, and what happens to Flyway without its starter: check your new test catches it);
   - test starters, `@MockBean` → `@MockitoBean`, `@AutoConfigureMockMvc`'s new package;
   - Jackson 3: the customiser and the `JsonGenerator` feature's new home;
   - renamed properties (add `spring-boot-properties-migrator` temporarily, read its report, fix, remove it).
4. Get `./mvnw verify` green with the JSON contract unchanged. Start the app and check `/actuator/health/readiness`.
5. Write `UPGRADE.md`: every change, why it was needed, and what you'd check before doing this on a production service.

Stretch: on a fresh copy, run the OpenRewrite Spring Boot 4 recipe and compare its diff with yours.

No solution is published; mentors review `UPGRADE.md`.
