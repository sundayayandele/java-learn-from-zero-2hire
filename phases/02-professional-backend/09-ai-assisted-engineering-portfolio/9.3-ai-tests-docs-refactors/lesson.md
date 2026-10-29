## What you will be able to do

- Use AI assistants to **write tests** that add real value: boundary cases, failure paths, test data builders and WireMock stubs, without accepting test illusions.
- Generate and maintain **documentation**: READMEs, Javadoc, ADR drafts, OpenAPI descriptions and runbooks, checked for accuracy.
- Carry out **refactors and upgrades** with AI help safely: characterisation tests first, small steps, tool-assisted migrations (OpenRewrite) where they exist.
- Keep **your own understanding** growing while you use AI, so you remain interview-ready and able to debug without it.

## Why this matters in a bank or fintech

The most valuable uses of AI in established codebases are often not new features but the work teams postpone: missing tests around legacy code, outdated documentation, upgrades (Java 8 to 21, Spring Boot 3 to 4) and cleanup. These are exactly the areas where a mistake can silently change behaviour in production. Done carefully, AI help gets this work done; done carelessly, it creates a false sense of safety.

## 1. Tests with AI help

### What to ask for

Good requests are specific about **behaviour** and **cases**:

```text
Write JUnit 5 + AssertJ tests for FeeCalculator.transferFee(Money, CustomerType).
Rules (from docs/fees.md): ₦10.75 up to and including ₦5,000.00; ₦26.88 up to and
including ₦50,000.00; ₦53.75 above; staff pay nothing; amounts must be positive.
Use a @ParameterizedTest with @CsvSource for the boundaries (exactly on and 1 kobo
past each), separate tests for staff and invalid amounts, and AssertJ's
isEqualByComparingTo for money. Do not mock Money or FeeSchedule.
```

Other productive requests:

- "List the edge cases you would test for `InterestAccrual.dailyInterest`, without writing code yet." Then pick the ones that matter.
- "Create a test data builder for `Account` following the pattern in `TransferTestData`."
- "Write WireMock stubs for the gateway's initialise endpoint: success, 503 then success, 6-second delay, and a 400 with this error body."
- "Write a Testcontainers integration test for the 20-concurrent-transfers scenario using our `TestcontainersConfiguration`."

### How to judge the result

1. **Do the tests encode the requirement, or the current code?** Generated tests often mirror the implementation, including its bugs. Write (or at least decide) the expected values from the **specification**, not from running the code.
2. **Would each test fail if the code were wrong?** Break the code deliberately (or run PIT, topic 7.4) and see.
3. **Are the assertions meaningful?** Watch for no assertions, assertions on mocks' return values, and `assertNotNull` as the only check.
4. **Are they maintainable?** Clear names, no duplication, consistent with existing tests.

A powerful pattern is **test-first with AI**: you write the failing test (the specification), and the assistant writes the implementation until it passes. You keep control of *what* the system must do.

## 2. Documentation with AI help

AI is good at turning code into prose and at keeping structure consistent:

- **README sections:** "Write a 'How to run locally' section for this repository based on compose.yaml, .env.example and the Maven wrapper."
- **Javadoc** for public APIs, especially explaining *why* (ask it to include the rounding rule and the reason).
- **OpenAPI descriptions and examples** from your controllers and DTOs.
- **ADR drafts:** give the context, options and decision; let it structure the Architecture Decision Record, then edit.
- **Runbooks:** "From ApiExceptionHandler, the Actuator configuration and the resilience settings, draft a runbook section for 'payment gateway unavailable'."
- **Diagrams as code:** Mermaid sequence diagrams of a flow you describe (Phase 1 architecture packs used the same format).

Check every factual statement: commands, property names, ports, versions and especially claims about behaviour ("retries three times") against the code and configuration. Documentation that is fluent and wrong is worse than none. Keep documentation next to the code and update it in the same pull request as the change.

## 3. Refactoring and upgrades with AI help

### Characterisation tests first

Before refactoring code you do not fully understand (common with legacy bank code), pin down its current behaviour with **characterisation tests**: tests that record what the code does now, right or wrong, so you notice any change.

```text
Write characterisation tests for LegacyStatementGenerator.generate(...) using the three
sample inputs in src/test/resources/statements/. Assert the exact current output
(approval/snapshot style). Do not change production code.
```

Then refactor in **small, behaviour-preserving steps**, running the tests after each, and commit each step. Ask the assistant for one step at a time ("extract the opening-balance calculation into a method", "replace this loop with a stream"), not "clean up this class".

### Use deterministic tools where they exist

For large mechanical migrations, prefer **recipe-based tools** that are tested and repeatable over free-form generation:

- **OpenRewrite** recipes for Java version upgrades, Spring Boot 3 → 4, JUnit 4 → 5, Jakarta namespace changes (Phase 1 topics 1.12 and 4.7):

```bash
./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE \
  -Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0
```

- IntelliJ IDEA's structural refactorings (rename, extract, inline, change signature) are deterministic and safe.

Use the AI assistant for what recipes do not cover: explaining a failing test after the upgrade, adapting custom code, writing the upgrade notes.

### Keep the diff reviewable

Split upgrades into: dependency bumps; automated recipe changes; manual fixes; cleanup. Each is a separate pull request a reviewer can trust.

## 4. Keeping your understanding

AI can make you faster today and weaker in six months if it replaces the thinking that builds skill. Interviews (topic 9.5) and production incidents happen without an assistant. Habits that keep you sharp:

- **Predict before you ask:** write down what you think the answer or design is, then compare.
- **Explain it back:** after accepting code, explain it in your own words in the PR description.
- **Do some work unaided:** practise live-coding problems and debugging sessions without AI every week.
- **Ask "why", not just "how":** "Why does ordered locking prevent deadlocks here?" teaches more than "fix the deadlock".
- **Read the primary sources** the assistant summarises: Spring reference docs, JEPs, RFCs.
- Keep a learning log of mistakes the assistant made; they teach you what to check.

## 5. A worked mini-workflow

Goal: add idempotency to a legacy refund endpoint.

1. Ask the assistant to explain the current refund flow and list the tables it touches; verify by reading the code.
2. Write characterisation tests for the current behaviour with its help; review them.
3. Write, yourself, the failing tests for the new behaviour (same key replays; different payload with the same key returns 422; concurrent duplicates create one refund).
4. Ask the assistant to implement it following the existing idempotency pattern in transfers; review the diff with the topic 9.2 checklist.
5. Ask it to draft the ADR and the README section; correct them.
6. Open a small pull request with an AI-usage note.

## Common mistakes

- Generating tests from the code and treating them as proof the code is right.
- Accepting documentation without checking commands, properties and behaviour claims.
- "Refactor this whole class" in one step with no characterisation tests.
- Free-form AI rewrites where a deterministic OpenRewrite recipe exists.
- Mixing upgrade, refactor and feature changes in one pull request.
- Never practising without the assistant.

## Practice

1. Pick an under-tested class in your capstone; list the cases from the requirements yourself, then have an assistant implement the tests; run PIT and compare the mutation score before and after.
2. Generate a README "How to run" section and a runbook entry with an assistant; correct every inaccuracy and note how many there were.
3. Write characterisation tests for a piece of Phase 1 legacy-style code, then refactor it in five small committed steps.
4. Run the OpenRewrite Spring Boot 4 recipe on the Phase 1 upgrade lab and use an assistant only for the remaining manual fixes; write the upgrade notes.

## Key terms

Specification-based test, implementation-mirroring test, test-first with AI, test data builder, mutation testing, documentation drift, ADR, runbook, docs-as-code, characterisation test, approval (snapshot) test, behaviour-preserving refactor, OpenRewrite recipe, deterministic migration, reviewable diff, deliberate practice.

## Further reading

- [OpenRewrite documentation: Running recipes](https://docs.openrewrite.org/running-recipes/getting-started)
- [OpenRewrite: Spring Boot 4 migration recipes](https://docs.openrewrite.org/recipes/java/spring/boot4)
- [Wikipedia: Characterization test (Michael Feathers)](https://en.wikipedia.org/wiki/Characterization_test)
- [Martin Fowler: Refactoring (catalogue)](https://refactoring.com/catalog/)
- [ADR GitHub organisation: Architecture Decision Records](https://adr.github.io/)
- [GitHub Docs: Writing tests with GitHub Copilot](https://docs.github.com/en/copilot/tutorials/write-tests)
