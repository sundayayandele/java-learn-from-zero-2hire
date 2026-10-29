## What you will be able to do

- Explain "database per service" and why services must not share tables.
- Explain why distributed transactions (two-phase commit, XA) are avoided, and what replaces them.
- Design a multi-step money movement as explicit states with reservations and compensations.
- Make every cross-service step idempotent with business keys and unique constraints.
- Query across services without joins (API composition, read models), and reconcile to catch what slips through.

## Why this matters in a bank or fintech

In a monolith, a transfer is one database transaction: debit, credit, done, or nothing. Split Accounts and Ledger into services with their own databases, and that single transaction is gone. Get this wrong and you get the incidents banks fear most: a customer debited but the beneficiary never credited, a reversal that runs twice, a ledger that doesn't balance at end of day. Getting it right is mostly about **explicit states, idempotency and reconciliation**, which is exactly how card schemes and interbank systems such as NIP have always worked.

## 1. Database per service

Each service **owns** its data: only it reads and writes its tables, and others use its API or its events. This is what makes services independently deployable: Accounts can change its schema without breaking Transfers.

You can implement "own database" as separate database servers, separate databases on one server, or separate **schemas with separate credentials** on one server (cheapest, and fine to start). What matters is that Transfers *cannot* connect to the Accounts schema.

The price: **no joins and no transactions across services.** Everything below is about paying that price safely.

## 2. Why not distributed transactions?

**Two-phase commit (2PC)**, with XA in Java, coordinates several databases so they all commit or all roll back: a coordinator asks each participant to *prepare*, then tells all to *commit*. It's avoided between services because:

- **Availability:** every participant must be up at the same time; one slow service blocks all of them, holding locks while it waits.
- **Coupling:** all services must use XA-capable resources and a shared coordinator; Kafka, REST APIs and most cloud services can't take part.
- **Failure modes:** if the coordinator dies between phases, participants stay "in doubt" holding locks until someone intervenes.

Instead, services use **local transactions plus coordination in the business process**: the saga, which Module 11 builds with events. This topic sets up the thinking.

## 3. Make the process explicit: states, reservations, compensations

Model a transfer as a **state machine** owned by the Transfers service:

```
REQUESTED ──reserve funds (Accounts)──> RESERVED ──post journal (Ledger)──> POSTED
     │                                      │
     └── reserve rejected ──> REJECTED       └── posting failed ──> release hold ──> FAILED
```

- **Reserve, don't debit.** Accounts places a **hold** that reduces the available balance but can be released. This is how card authorisations work: an authorisation hold, then a clearing debit.
- **Each step is a local transaction** in one service.
- **Every forward step has a compensation**: "release hold" undoes "reserve funds". Compensations are business actions, recorded, never silent deletes.
- **The transfer's state is persisted after each step**, so a crash resumes from where it stopped instead of starting again.

The customer sees `PENDING` until the transfer reaches `POSTED` or `FAILED`. That's not a weakness; banking customers already know "pending".

## 4. Idempotency everywhere

Networks drop responses, so callers retry, and messages get delivered twice (Module 11). Every step must be safe to repeat:

- Use a **business key** that travels with the request: the transfer reference.
- Enforce it in the database, not just in code: `UNIQUE (account_id, reference)` on holds, `UNIQUE (reference)` on journals.
- On a duplicate, **return the original result** rather than an error, so a retry after a lost response still succeeds.

```sql
CREATE TABLE hold (
    id          BIGSERIAL PRIMARY KEY,
    account_id  TEXT          NOT NULL,
    reference   TEXT          NOT NULL,
    amount      NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status      TEXT          NOT NULL CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED')),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (account_id, reference)
);
```

```java
@Transactional
public Hold reserve(String accountId, String reference, BigDecimal amount) {
    return holds.findByAccountIdAndReference(accountId, reference)          // repeat: same answer
            .orElseGet(() -> {
                int updated = accounts.debitAvailable(accountId, amount);    // UPDATE ... SET available = available - :amount
                if (updated == 0) throw new InsufficientFundsException(accountId);   //   WHERE id = :id AND available >= :amount
                return holds.save(new Hold(accountId, reference, amount));
            });
}
```

The conditional `UPDATE ... WHERE available >= :amount` (topic 3.2) makes the check-and-debit atomic under concurrency; the unique constraint catches two simultaneous requests with the same reference.

## 5. Consistency models in plain words

- **Strong consistency:** every read sees the latest write. You get it **inside** one service's database transaction.
- **Eventual consistency:** after a change, other services catch up within a short time. You get it **between** services.

Design so that the rules that must never be broken (no overdraft, no double posting) are enforced **inside one service, strongly**, and everything else can lag briefly. "Available balance" lives in Accounts and is always exact; the statement view in another service may lag by a second, and the UI says so.

## 6. Reading across services

Without joins, three options:

1. **API composition:** a service (or a backend-for-frontend) calls several services and combines the results. Simple; fine for a few calls per screen. Mind latency and failures (topic 10.3).
2. **Read models (CQRS-style):** a service subscribes to other services' events and keeps its own query-friendly copy, such as a statements service holding the transactions of every account. Fast reads, at the cost of eventual consistency and more moving parts.
3. **Duplicate what you need, owned by events:** Transfers stores the beneficiary name it was given at request time; it doesn't fetch it again later.

Never solve it by reading another service's database "just for reports". Give reporting its own read model or a data warehouse fed by events.

## 7. Reconciliation: the safety net

Even with sagas and idempotency, something will eventually slip through: a bug, a message lost before the outbox existed, a manual database fix. Banks **reconcile** daily: compare two independent records and investigate breaks.

- Every `POSTED` transfer has exactly one balanced journal in the Ledger, and vice versa.
- Every `ACTIVE` hold older than, say, 15 minutes belongs to a transfer still in progress; otherwise release it and raise an alert.
- Sum of available balances plus active holds equals the ledger balance per account.

Run these as scheduled jobs (Module 11's Spring Batch topic builds an end-of-day run), emit a metric for the number of breaks, and alert when it's above zero.

## Worked example: a transfer across Transfers, Accounts and Ledger

1. `POST /transfers` with reference `TRF-2026-0042` arrives. Transfers inserts it as `REQUESTED` (unique reference: a duplicate request returns the existing transfer).
2. Transfers calls `POST /accounts/A1/holds` with the reference. Accounts reserves ₦25,000 in one local transaction. Transfers stores `RESERVED`. If Accounts answers `422`, Transfers stores `REJECTED` and tells the customer.
3. Transfers asks Ledger to post a journal (debit A1, credit A2) with the same reference; the journal's unique reference makes this idempotent. Transfers stores `POSTED`, and Accounts captures the hold.
4. If posting fails permanently, Transfers calls `DELETE /accounts/A1/holds/TRF-2026-0042` (release) and stores `FAILED`.
5. If Transfers crashes between steps 2 and 3, a scheduled job picks up transfers stuck in `RESERVED` and continues them; the idempotent steps make repeating safe.
6. At 23:00 the reconciliation job compares holds, transfers and journals, and reports zero breaks.

In Module 11 the calls in steps 3 and 4 become events through a transactional outbox, so they survive a crash without the scheduled clean-up doing the heavy lifting.

## Common mistakes

- Sharing a database between services, or "just reading" another service's tables.
- Debiting immediately instead of reserving, so a later failure needs a refund.
- Relying on code checks without unique constraints, so two concurrent requests both pass.
- Returning an error for a duplicate request instead of the original result.
- Keeping state only in memory between steps, so a restart loses in-flight transfers.
- No reconciliation, so inconsistencies are found by customers.

## Practice

1. Draw the transfer state machine for ITL Pay with every state, transition and compensation, and store it as ADR 2.
2. Implement holds with the table above, a conditional update and idempotent `reserve`; prove with a test that 50 concurrent identical requests create exactly one hold.
3. Add the release compensation, and a scheduled job that releases holds older than 15 minutes whose transfer isn't in progress.
4. Write the reconciliation query that finds `POSTED` transfers without a journal, and journals without a transfer.
5. Explain to a product manager, in five sentences, why a transfer shows "pending" for a moment and why that is safer.

## Key terms

Database per service, data ownership, two-phase commit, XA, local transaction, saga, state machine, reservation (hold), compensation, idempotency, business key, unique constraint, strong consistency, eventual consistency, API composition, read model, CQRS, reconciliation, break.

## Further reading

- [microservices.io: Database per service](https://microservices.io/patterns/data/database-per-service.html)
- [microservices.io: Saga](https://microservices.io/patterns/data/saga.html)
- [Martin Fowler: CQRS](https://martinfowler.com/bliki/CQRS.html)
- [Pat Helland: Life beyond Distributed Transactions (ACM Queue)](https://queue.acm.org/detail.cfm?id=3025012)
- [Stripe engineering: Designing robust and predictable APIs with idempotency](https://stripe.com/blog/idempotency)
