# Reference solution: Core banking ledger

← [Capstone brief](../README.md)

> **Build your own first.** This is one good solution, not the only one. Compare it with yours after you've finished, and focus on the *decisions*: where invariants are enforced, how locking works, and how idempotency is guaranteed.

Java 21 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · Spring Data JPA + JdbcClient · Testcontainers. 22 tests, all green.

## Run it

```bash
cp .env.example .env            # set DB_PASSWORD
docker compose up --build       # app on :8080, PostgreSQL on :5432
./mvnw verify                   # tests (Docker must be running)
```

```bash
# Open two accounts
curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' -d '{"holderName":"Ada Okafor"}'
curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' -d '{"holderName":"Bola Ade"}'
# Deposit, then transfer (fee applied), then retry the same transfer (returns 200, replayed=true)
curl -s -X POST localhost:8080/api/v1/deposits  -H 'Content-Type: application/json' -d '{"accountNumber":"1000000001","amount":50000.00,"reference":"dep-1"}'
curl -s -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' -d '{"fromAccount":"1000000001","toAccount":"1000000002","amount":20000.00,"reference":"trf-1","narration":"rent"}'
curl -s -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' -d '{"fromAccount":"1000000001","toAccount":"1000000002","amount":20000.00,"reference":"trf-1","narration":"rent"}'
# Statement, trial balance, consistency check
curl -s "localhost:8080/api/v1/accounts/1000000001/statement?from=2026-10-01&to=2026-10-31"
curl -s localhost:8080/api/v1/ledger/trial-balance
curl -s localhost:8080/api/v1/ledger/consistency
```

## Design

```text
HTTP ─► AccountController / PaymentController / LedgerController   (DTO records, Bean Validation, Problem Details)
          │
          ▼
        LedgerService  ── @Transactional: lock customer rows (id order) → idempotency check → rules → post → save
          │                  FeeSchedule (tiers from @ConfigurationProperties)
          ├── AccountRepository / JournalRepository   (Spring Data JPA, pessimistic locks)
          └── LedgerQueries                            (JdbcClient: daily limit, statements, trial balance)
          ▼
        PostgreSQL ── constraints + triggers enforce the invariants
```

### Decisions worth discussing in an interview

1. **Invariants live in the database, too.** The schema rejects negative customer balances, duplicate references and a second reversal of the same journal. A trigger makes postings immutable, and a *deferred constraint trigger* checks at commit that every journal's postings balance per currency. A bug in any future service can't break the ledger silently. (`src/main/resources/db/migration/V1__ledger_schema.sql`)
2. **Stored balance plus postings.** Postings are the record; `account.balance` is a cache updated in the same transaction for fast reads. `/ledger/consistency` proves they agree, and the concurrency test asserts it.
3. **Locking.** Every operation locks the customer accounts it touches with `SELECT ... FOR UPDATE` in **id order** (one query, `ORDER BY a.id`), then internal accounts last. That serialises competing transfers across any number of instances and makes deadlocks impossible. The trade-off: the `FEE_INCOME` row is a hot spot that every transfer updates. At high volume you would shard it into several fee-income accounts or post fees asynchronously; at Phase 1 volumes it's fine.
4. **Idempotency.** The reference is checked *after* the locks are held, so a retry waits for the first attempt to commit and then gets its receipt back (200, `replayed: true`). A different payload with the same reference is 409. The unique constraint is the backstop when two new requests race on different accounts.
5. **Reversal lock order.** The service looks up the account numbers of the original journal with plain SQL, locks them, and *then* loads the journal. Loading entities before locking them would leave stale balances in the persistence context.
6. **Reads in SQL, writes in JPA.** Statements use a window function for the running balance and are paged; the trial balance and consistency check are single aggregate queries. JPA is used where its features help (entities with behaviour, locking, dirty checking).
7. **Money.** `NUMERIC(19,2)` and `BigDecimal` everywhere; amounts limited to 2 decimal places at the API boundary with `@Digits`, so there is no rounding inside the ledger at all. Fees are fixed amounts per tier.
8. **Time.** An injected `Clock` and a configured business zone (`Africa/Lagos`) define "today" for daily limits; timestamps are stored as `timestamptz`.

### What I'd do next

- An outbox table written in the same transaction for `TransferCompleted` events (Module 11).
- Multi-currency with FX journals using Capstone 2.
- Optimistic-locking variant and a benchmark against pessimistic locking under contention.
- Security (Module 6): authentication, and customers seeing only their own accounts.

## Tests

| Test | What it proves |
| --- | --- |
| `FeeScheduleTest` | Fee tier boundaries; configuration order doesn't matter |
| `PaymentControllerTest` (`@WebMvcTest`) | Validation errors and exception mapping, with the service mocked |
| `LedgerApiIntegrationTest` | Deposits, transfers, fees, overdraft rejection, idempotent replay and 409 on payload change, frozen accounts, daily limit including fees, reversal once only, statement maths, database-level posting immutability |
| `ConcurrencyIntegrationTest` | 50 simultaneous transfers among 10 accounts: totals preserved, no negative balances, stored balances equal postings, no deadlocks |
| `LedgerApplicationTests` | Migrations ran, readiness is UP, unknown paths return Problem Details |

## Data

All data is created through the API. No external dataset.
