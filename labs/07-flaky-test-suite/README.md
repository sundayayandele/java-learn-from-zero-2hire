# Lab 7.D: Make a flaky test suite trustworthy

← [Module 7](../../phases/02-professional-backend/07-testing-code-quality/README.md) · [Topic 7.1](../../phases/02-professional-backend/07-testing-code-quality/7.1-junit-mockito-tdd/README.md) · [Topic 2.6](../../phases/01-foundations/02-concurrency-jvm/2.6-debugging-troubleshooting/README.md)

"It passes on my machine" is how flaky tests begin. This suite of eight tests is green with `./mvnw test`, but `./mvnw test -Phostile` simulates a CI runner that is **in another time zone and locale, slower, runs test methods in a different order and runs test classes in parallel**. Under those conditions five tests fail. A suite that passes only sometimes is worse than no suite: people learn to ignore it.

**Goal:** both commands green, five times in a row each, without deleting or `@Disabled`-ing tests and without editing `pom.xml` or the `harness` package.

| Test | Hidden assumption to find |
| --- | --- |
| `IdGeneratorTest` | Tests share hidden static state and depend on the order they run in |
| `BusinessDateTest` | The code uses the machine's time zone instead of the bank's (Lagos) |
| `AmountFormatterTest` | The code uses the machine's locale for number formatting (and divides kobo with `double`) |
| `NotifierTest` | The test sleeps and hopes background work has finished |
| `SmsClientTest` and `OtpDeliveryTest` | Two tests start a fake server on the same fixed port |

## How to work

1. Run both commands and read the failures. Reproduce each one on purpose (for example run the default profile with `-Duser.timezone=Asia/Tokyo`).
2. Fix the **design**, not just the test: inject what varies (a `Clock` or `ZoneId`, a `Locale`, an `Executor`, a base URL), remove hidden static state, and use port 0 (an ephemeral port) for test servers. Awaitility (topic 7.1) is an alternative to injecting the executor when real threads are needed.
3. Loop each profile five times: `for i in 1 2 3 4 5; do ./mvnw -q test -Phostile || break; done`.
4. Write `FINDINGS.md`: for each flaky test, the hidden assumption, how you proved it, and the design change. Add a short "flaky test policy" for a team (what to do when a test fails intermittently on `main`).

No solution is published; mentors review your changes and `FINDINGS.md`.
