## What you will be able to do

- Explain ACID and use `BEGIN`/`COMMIT`/`ROLLBACK` and savepoints.
- Describe the concurrency anomalies (dirty read, non-repeatable read, phantom, lost update, write skew) and which isolation levels prevent them in PostgreSQL and MySQL.
- Explain how PostgreSQL's MVCC works and why readers don't block writers.
- Prevent double-spending with pessimistic locking (`SELECT ... FOR UPDATE`), optimistic locking (version columns) and atomic conditional updates.
- Avoid and handle deadlocks and serialization failures with ordered locking and retries.

## Why this matters in a bank or fintech

Topic 2.2 ended with a warning: in-memory locks cannot protect a balance shared by several service instances. The database can. Transactions are how a transfer debits one account and credits another so that either both happen or neither does, even if the server crashes in between. Isolation and locking are how two simultaneous withdrawals from the same account are serialised so the customer cannot spend the same naira twice. This is the most important database topic for a payments engineer.

## 1. Transactions and ACID

A **transaction** groups statements into one unit of work.

```sql
BEGIN;
UPDATE account SET balance = balance - 5000.00 WHERE number = '0123456789';
UPDATE account SET balance = balance + 5000.00 WHERE number = '9876543210';
INSERT INTO ledger_entry (...) VALUES (...), (...);
COMMIT;      -- or ROLLBACK; to undo everything since BEGIN
```

**ACID** describes the guarantees:

- **Atomicity**: all statements succeed or none take effect. A crash after the debit and before the credit leaves no trace of the debit.
- **Consistency**: the transaction moves the database from one valid state to another; every constraint (foreign keys, `CHECK`, `UNIQUE`) holds at commit.
- **Isolation**: concurrent transactions don't see each other's partial work, to a degree set by the **isolation level**.
- **Durability**: once `COMMIT` returns, the change survives crashes (PostgreSQL writes it to the write-ahead log, WAL, first).

**Savepoints** let you roll back part of a transaction:

```sql
BEGIN;
INSERT INTO txn (...) VALUES (...);
SAVEPOINT before_fee;
INSERT INTO txn (...) VALUES (...);   -- fee posting fails a check
ROLLBACK TO SAVEPOINT before_fee;     -- keep the main posting
COMMIT;
```

Keep transactions **short**. A transaction that stays open while your code calls an external API holds locks and prevents vacuum from cleaning up, hurting everyone else.

## 2. Concurrency anomalies

| Anomaly | What happens | Banking example |
| --- | --- | --- |
| **Dirty read** | Read another transaction's uncommitted data | A balance check sees a deposit that is later rolled back |
| **Non-repeatable read** | Same row read twice returns different values | A report reads a balance at the start and the end and gets different numbers |
| **Phantom read** | Same query returns different *sets of rows* | `count(*)` of today's transactions changes between two reads in a report |
| **Lost update** | Two read-modify-write cycles overwrite each other | Two withdrawals both read ₦1,000 and both write their own result |
| **Write skew** | Two transactions read overlapping data, each writes a different row, together breaking a rule | Two joint-account holders each withdraw because "the combined balance is enough" |

## 3. Isolation levels

The SQL standard defines four levels. PostgreSQL implements three distinct behaviours:

| Level | Dirty read | Non-repeatable read | Phantom | PostgreSQL notes |
| --- | --- | --- | --- | --- |
| Read Uncommitted | prevented in PG | possible | possible | Behaves as Read Committed |
| **Read Committed** (PG default) | prevented | possible | possible | Each *statement* sees data committed before it began |
| **Repeatable Read** | prevented | prevented | prevented in PG | Whole transaction sees one snapshot; conflicting writes fail |
| **Serializable** | prevented | prevented | prevented | As if transactions ran one at a time; may abort with a serialization failure |

```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
...
COMMIT;
```

**MySQL (InnoDB)** defaults to Repeatable Read and uses a mix of snapshots and next-key (gap) locks to prevent phantoms in locking reads. Differences between databases matter: always check the documentation for the one you use.

The key insight: **Read Committed alone does not prevent lost updates** in a read-then-write pattern done in application code. You need one of the techniques in section 5.

## 4. How PostgreSQL does it: MVCC

PostgreSQL uses **multi-version concurrency control**. An `UPDATE` does not overwrite a row; it writes a new version of it and marks the old one as expired. Each transaction sees the versions that were committed as of its **snapshot**.

Consequences:

- **Readers never block writers, and writers never block readers.** A long report doesn't stop payments from posting.
- **Writers block writers on the same row.** A second `UPDATE` of a row waits until the first transaction commits or rolls back.
- Old row versions accumulate and are cleaned up by **VACUUM** (autovacuum). Very long transactions prevent cleanup and cause table "bloat".

## 5. Preventing double spending

Starting point, broken under concurrency:

```java
BigDecimal balance = jdbc.queryBalance(acct);            // SELECT balance ...
if (balance.compareTo(amount) < 0) throw new InsufficientFundsException(...);
jdbc.update("UPDATE account SET balance = ? WHERE id = ?", balance.subtract(amount), acct);
```

Two transactions can both read ₦1,000 and both write ₦200 (a lost update). Three correct alternatives:

### Option A: atomic conditional update (simplest, often best)

```sql
UPDATE account
SET balance = balance - :amount
WHERE id = :id AND balance >= :amount;
```

The check and the change happen in one statement on one row; PostgreSQL's row lock serialises concurrent updates, and the second one re-evaluates the `WHERE` against the new balance. The application checks the **affected row count**: 1 means success, 0 means insufficient funds (or no such account).

### Option B: pessimistic locking with `SELECT ... FOR UPDATE`

```sql
BEGIN;
SELECT balance, status FROM account WHERE id = :id FOR UPDATE;   -- locks the row until commit
-- application checks status, limits, balance...
UPDATE account SET balance = balance - :amount WHERE id = :id;
INSERT INTO ledger_entry ...;
COMMIT;
```

Any other transaction that tries to lock or update that row waits. Use this when the decision needs several reads and checks in application code. Variants: `FOR UPDATE NOWAIT` (fail immediately if locked) and `FOR UPDATE SKIP LOCKED` (skip locked rows, which is perfect for a queue of payment jobs processed by several workers):

```sql
SELECT id FROM payment_job WHERE status = 'PENDING'
ORDER BY created_at LIMIT 10
FOR UPDATE SKIP LOCKED;
```

### Option C: optimistic locking with a version column

```sql
ALTER TABLE account ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- read
SELECT balance, version FROM account WHERE id = :id;          -- e.g. version = 7
-- write only if nobody changed it in between
UPDATE account SET balance = :newBalance, version = version + 1
WHERE id = :id AND version = 7;                                 -- 0 rows → someone else won; retry
```

No locks are held between read and write; conflicts are detected at write time. It suits low-contention data and long user think-time (editing a customer profile). JPA's `@Version` does exactly this (topic 4.3). Under high contention (a hot merchant settlement account), many retries make pessimistic locking or Option A better.

### Option D: let the database prove it with Serializable

Run the transaction at `SERIALIZABLE` and **retry on serialization failure** (SQLSTATE `40001`). PostgreSQL's Serializable Snapshot Isolation detects dangerous patterns, including write skew, and aborts one transaction. It is the most general protection, at the cost of more aborts under contention, and it **requires retry logic** in the application.

## 6. Deadlocks in the database

Exactly as in topic 2.2, two transactions that lock rows in opposite orders deadlock:

```text
T1: UPDATE account ... WHERE id = 1;   T2: UPDATE account ... WHERE id = 2;
T1: UPDATE account ... WHERE id = 2;   (waits for T2)
T2: UPDATE account ... WHERE id = 1;   (waits for T1)  → deadlock
```

PostgreSQL detects it after `deadlock_timeout` (1 s by default) and aborts one transaction with SQLSTATE `40P01`. Prevention: **lock rows in a consistent order** (for transfers, lock the lower account id first, or `SELECT ... WHERE id IN (:a, :b) ORDER BY id FOR UPDATE`). Handling: retry the aborted transaction.

### Retrying correctly

Serialization failures (`40001`) and deadlocks (`40P01`) are expected, transient errors. The whole transaction must be retried from the start, a small number of times, with a short random backoff, and only if the operation is **idempotent** (the same transfer reference cannot be applied twice: the `UNIQUE` constraint from topic 3.1 helps).

## 7. Other locks you will meet

- `LOCK TABLE` and DDL locks: `ALTER TABLE` takes strong locks; in production, schema changes must be planned (topic 3.4).
- **Advisory locks** (`pg_advisory_xact_lock(key)`): application-defined locks, for example to ensure only one instance runs the end-of-day job.
- **Lock timeouts**: `SET lock_timeout = '2s'` so a request fails fast instead of waiting forever behind a stuck transaction.

## Worked example: a correct transfer

```sql
BEGIN;
-- Lock both accounts in id order to avoid deadlocks
SELECT id, balance, status FROM account
WHERE id IN (:fromId, :toId)
ORDER BY id
FOR UPDATE;

-- (application checks: both ACTIVE, limits OK)

UPDATE account SET balance = balance - :amount
WHERE id = :fromId AND balance >= :amount;          -- must affect exactly 1 row, else ROLLBACK

UPDATE account SET balance = balance + :amount
WHERE id = :toId;

INSERT INTO ledger_entry (transfer_ref, account_id, direction, amount)
VALUES (:ref, :fromId, 'D', :amount), (:ref, :toId, 'C', :amount);   -- UNIQUE(transfer_ref, direction)
COMMIT;
```

Atomic (all or nothing), consistent (constraints hold), isolated (row locks serialise competing transfers), durable (committed to the WAL), deadlock-free (ordered locks) and idempotent (the unique transfer reference rejects a replay). In Module 4, Spring's `@Transactional` will manage `BEGIN`/`COMMIT`/`ROLLBACK` for you, but the SQL underneath is exactly this.

## Common mistakes

- Read-then-write balance updates in application code with no locking.
- Assuming the default isolation level prevents lost updates.
- Long transactions spanning HTTP calls to other services.
- Locking rows in inconsistent order.
- Using Serializable without retry logic.
- Retrying non-idempotent operations.
- Forgetting to check affected row counts.

## Practice

1. Open two `psql` sessions. Reproduce a lost update with read-then-write, then fix it with each of: conditional `UPDATE`, `SELECT ... FOR UPDATE`, and a version column. Write down what each session sees and when it blocks.
2. Reproduce a deadlock with two sessions updating two accounts in opposite orders. Read the error message, then fix it with ordered locking.
3. In two sessions at `REPEATABLE READ`, reproduce write skew on a "joint account" rule (sum of two balances must stay ≥ 0). Then run the same at `SERIALIZABLE` and observe the `40001` failure.
4. Build a job queue table and process it from three sessions with `FOR UPDATE SKIP LOCKED`, showing no job is picked twice.

## Key terms

Transaction, ACID, commit, rollback, savepoint, WAL, isolation level, dirty read, non-repeatable read, phantom, lost update, write skew, MVCC, snapshot, VACUUM, row lock, `SELECT FOR UPDATE`, `NOWAIT`, `SKIP LOCKED`, optimistic locking, version column, serialization failure, deadlock, SQLSTATE, idempotency, advisory lock, lock timeout.

## Further reading

- [PostgreSQL: Transaction Isolation](https://www.postgresql.org/docs/17/transaction-iso.html)
- [PostgreSQL: Explicit Locking](https://www.postgresql.org/docs/17/explicit-locking.html)
- [PostgreSQL: Concurrency Control (MVCC introduction)](https://www.postgresql.org/docs/17/mvcc-intro.html)
- [MySQL 8.4: InnoDB Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html)
- [Oracle: java.sql.Connection API (transaction isolation, Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)
