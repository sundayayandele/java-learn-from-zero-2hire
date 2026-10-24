# Lab 5.E: Fix a fragile payment integration

← [Module 5](../../phases/02-professional-backend/05-api-design-integration/README.md) · [Topic 5.4](../../phases/02-professional-backend/05-api-design-integration/5.4-consuming-apis-resilience/README.md) · [Topic 5.3](../../phases/02-professional-backend/05-api-design-integration/5.3-errors-pagination-idempotency/README.md)

A small Spring Boot 4 service that starts payments at a provider and receives its webhooks. It "works" in a demo, but it has **five planted bugs** of the kind that cause real incidents: double charges, hung threads, rejected webhooks and leaked internals. `./mvnw test` runs five tests against a WireMock provider; **all five fail**. Make them pass by fixing the service, not by changing the tests.

| # | Failing test | What goes wrong in production | Topic |
| --- | --- | --- | --- |
| 1 | `bug1_aSlowProviderFailsFastInsteadOfHangingTheRequest` | A slow provider holds every request thread until the service stops responding | 5.4 timeouts |
| 2 | `bug2_initialiseIsNotRetriedBlindlyBecauseItIsNotIdempotent` | A retry after a 503 can create the same payment several times at the provider | 5.4 retries |
| 3 | `bug3_theSameIdempotencyKeyNeverCreatesASecondPayment` | A mobile app retrying after a timeout charges the customer twice | 5.3 idempotency |
| 4 | `bug4_webhookSignaturesAreCheckedOverTheExactBytesReceived` | Genuine webhooks are rejected (or forged ones could be accepted) because the signature is checked over re-serialised JSON | 5.4 webhooks, 6.4 |
| 5 | `bug5_providerInternalsNeverLeakToOurClients` | The provider's error text, including internal hosts, is returned to customers | 5.3 Problem Details, 6.4 |

## How to work

1. Run `./mvnw test` and read each failure. Reproduce each bug in your own words before fixing it.
2. Fix one bug at a time, with a small commit per bug (`fix(bug2): don't retry non-idempotent initialise`). Keep the other tests failing until you get to them.
3. Prefer the simplest correct fix: Spring Boot's `HttpClientSettings` for timeouts, no retries (or retries only for idempotent calls), an idempotency store keyed by `Idempotency-Key`, `@RequestBody byte[]` plus `MessageDigest.isEqual` for signatures, and a generic Problem Detail with the cause logged server-side.
4. When all five pass, run the suite five times to make sure nothing is timing-dependent.
5. Write `FINDINGS.md`: for each bug, the symptom, root cause, fix and the production consequence you prevented. Then answer: *what would still be missing before this service could take real money?* (Hint: persistence of idempotency keys, concurrent duplicates, circuit breaking, reconciliation.)

The idempotency store can be an in-memory map in this lab; say in `FINDINGS.md` why it must be a database table with a unique constraint in a real service.

No solution is published; mentors review your commits and `FINDINGS.md`.
