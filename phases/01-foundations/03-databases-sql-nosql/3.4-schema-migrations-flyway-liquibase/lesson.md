## What you will be able to do

- Explain why database schemas must be versioned like code, and how migration tools track what has run.
- Write and run Flyway migrations (versioned and repeatable) from Maven, the command line and Spring Boot.
- Write the equivalent Liquibase changelog and explain where Liquibase differs.
- Make backward-compatible ("expand and contract") changes that deploy with zero downtime.
- Handle failures, checksum mismatches and baselining of existing databases.

## Why this matters in a bank or fintech

A bank's core database has dozens of environments: every developer's laptop, CI, test, UAT, pre-production, production and DR. If schema changes are applied by hand, these drift apart, and one day a release fails in production because a column exists in UAT but not in prod. Auditors also ask "who changed this table, when, and was it reviewed?". Migration tools answer all of that: every change is a reviewed file in Git, applied automatically, in order, exactly once, everywhere.

## 1. The idea

A **migration** is a small, ordered script that moves the schema from one version to the next. The tool keeps a **history table** in the database recording which migrations have run, when, and with what checksum. On startup or deploy it compares the files on disk with the history and applies anything new, in order.

Rules that make this work:

- **Migrations are immutable once applied** to any shared environment. To change something, write a new migration.
- **Forward only** in practice. Rollbacks are another forward migration (or a restore). Many teams don't write "down" scripts at all.
- **Same files, every environment.** Data differences (test seed data) are handled separately.

## 2. Flyway

Flyway uses plain SQL files with a naming convention:

```text
src/main/resources/db/migration/
├── V1__create_customer_and_account.sql
├── V2__create_txn.sql
├── V3__add_account_daily_limit.sql
├── V4__index_txn_account_posted.sql
└── R__view_account_summary.sql
```

- `V<version>__<description>.sql`: **versioned** migrations, run once, in version order. Two underscores separate version and description.
- `R__<description>.sql`: **repeatable** migrations, re-run whenever their checksum changes, after all versioned ones. Good for views, functions and reference data.
- `U<version>__...sql`: undo migrations (a paid Flyway feature; most teams don't use them).

Example migrations:

```sql
-- V1__create_customer_and_account.sql
CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bvn        CHAR(11)     NOT NULL UNIQUE,
    full_name  VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number      CHAR(10)      NOT NULL UNIQUE,
    customer_id BIGINT        NOT NULL REFERENCES customer(id),
    currency    CHAR(3)       NOT NULL DEFAULT 'NGN',
    balance     NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version     BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_account_customer ON account (customer_id);
```

```sql
-- V3__add_account_daily_limit.sql
ALTER TABLE account ADD COLUMN daily_limit NUMERIC(19,2) NOT NULL DEFAULT 500000.00;
```

Flyway records each run in `flyway_schema_history`:

```text
 installed_rank | version | description                    | checksum    | success
----------------+---------+--------------------------------+-------------+---------
              1 | 1       | create customer and account    | 1384624371  | t
              2 | 2       | create txn                     | -982374623  | t
              3 | 3       | add account daily limit        | 47729384    | t
```

### Running Flyway

**With Spring Boot** (Module 4), add the starter and the database module. In Spring Boot 4, Flyway has its own starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>   <!-- PostgreSQL support is a separate module since Flyway 10 -->
</dependency>
```

Spring Boot runs pending migrations at startup before the application serves traffic. Useful properties: `spring.flyway.locations`, `spring.flyway.baseline-on-migrate`, `spring.flyway.clean-disabled=true` (the default; keep it so).

**From the command line or CI**, use the Flyway CLI or Docker image, which is common for running migrations as a separate deployment step before the new application version starts:

```bash
docker run --rm --network host -v "$PWD/src/main/resources/db/migration:/flyway/sql" \
  flyway/flyway -url=jdbc:postgresql://localhost:5432/bank -user=postgres -password=devpass \
  info migrate
```

Commands: `info` (status of every migration), `migrate`, `validate` (checksums match?), `repair` (fix the history table after a failed migration or a deliberate checksum change), `baseline` (adopt an existing database), `clean` (drop everything: **never** in shared environments).

### When things go wrong

- **Checksum mismatch**: someone edited an already-applied migration. Flyway refuses to continue. Fix: revert the edit and create a new migration. Use `repair` only when the change was intentional and harmless (for example, whitespace), and document why.
- **Failed migration**: PostgreSQL supports transactional DDL, so a failing migration rolls back cleanly, and you fix the file and re-run. (MySQL commits DDL implicitly, so a half-applied migration must be repaired by hand: keep MySQL migrations small.)
- **Existing database with no history**: `baseline` marks it as already at a given version, so only newer migrations run.
- **Out-of-order versions** from parallel branches (`V7` merged after `V8` was applied): by default Flyway rejects them. Agree on a version scheme (timestamps such as `V20261021_1430__...` avoid collisions), or set `outOfOrder` deliberately.

## 3. Liquibase

Liquibase tracks **changesets** in a **changelog** (XML, YAML, JSON or formatted SQL), identified by `id` + `author` + file, in a `DATABASECHANGELOG` table. Its abstractions can generate SQL for different databases, and it has built-in rollback definitions and preconditions.

```yaml
# src/main/resources/db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - changeSet:
      id: 3-add-account-daily-limit
      author: ada
      preConditions:
        - onFail: MARK_RAN
        - not:
            columnExists: { tableName: account, columnName: daily_limit }
      changes:
        - addColumn:
            tableName: account
            columns:
              - column:
                  name: daily_limit
                  type: NUMERIC(19,2)
                  defaultValueNumeric: 500000.00
                  constraints: { nullable: false }
      rollback:
        - dropColumn: { tableName: account, columnName: daily_limit }
```

Or as formatted SQL, which many teams prefer for readability:

```sql
--liquibase formatted sql
--changeset ada:3-add-account-daily-limit
ALTER TABLE account ADD COLUMN daily_limit NUMERIC(19,2) NOT NULL DEFAULT 500000.00;
--rollback ALTER TABLE account DROP COLUMN daily_limit;
```

In Spring Boot 4, use `spring-boot-starter-liquibase`.

| | Flyway | Liquibase |
| --- | --- | --- |
| Format | SQL (or Java) files with naming convention | XML/YAML/JSON/SQL changelogs |
| Ordering | Version number in file name | Order in the changelog |
| Identity | Version | id + author + file path |
| Rollback | Write a new migration (undo is a paid feature) | Rollback blocks in the changelog |
| Database-agnostic changes | You write the SQL | Abstract change types generate SQL |
| Learning curve | Very low | Moderate |

Both are fine. Flyway's plain SQL is easier for most teams and is what this programme uses; you should be able to read and extend a Liquibase changelog when you meet one, because many banks standardised on it.

## 4. Zero-downtime changes: expand and contract

In production, old and new versions of your service run **at the same time** during a rolling deploy. A migration must therefore work with both. Some changes are naturally safe; others must be split across releases.

**Safe in one step:** adding a table; adding a nullable column; adding a column with a constant default (PostgreSQL 11+ does this instantly); adding an index `CONCURRENTLY`.

**Dangerous in one step:** renaming or dropping a column the old version still reads; changing a column's type; adding `NOT NULL` without a default to a big table; creating an index without `CONCURRENTLY` on a hot table.

**Expand and contract** (also called parallel change) for renaming `account.number` to `account.nuban`:

1. **Expand** (release 1): add `nuban` (nullable). The application writes both columns and reads `number`.
2. **Migrate data**: backfill `nuban` from `number` in batches (`UPDATE ... WHERE id BETWEEN ? AND ?`), not in one giant transaction.
3. **Switch reads** (release 2): read `nuban`, still write both. Add `NOT NULL` and `UNIQUE` once the backfill is complete.
4. **Contract** (release 3): stop writing `number`; finally, drop the column.

It takes three releases instead of one, but at no point is the bank down or any version broken.

### Other production habits

- Set `lock_timeout` at the top of risky migrations (`SET lock_timeout = '5s';`) so a migration waiting behind a long transaction fails fast instead of queueing all traffic behind its lock.
- `CREATE INDEX CONCURRENTLY` cannot run inside a transaction. In Flyway, put it in its own migration and disable the transaction for that script (a `.conf` file next to it with `executeInTransaction=false`), or run it as a separate script.
- Large data backfills belong in batched jobs, not in a single migration.
- Test migrations against a copy of production-sized data before release.

## Worked example: adding a ledger with Flyway

```sql
-- V5__create_ledger_entry.sql
CREATE TABLE ledger_entry (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_ref  VARCHAR(40)   NOT NULL,
    account_id    BIGINT        NOT NULL REFERENCES account(id),
    direction     CHAR(1)       NOT NULL CHECK (direction IN ('D','C')),
    amount        NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    posted_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_ref_dir UNIQUE (transfer_ref, direction)
);
CREATE INDEX idx_ledger_account_posted ON ledger_entry (account_id, posted_at);
```

```sql
-- R__view_account_balance_check.sql  (re-runs when edited)
CREATE OR REPLACE VIEW v_account_balance_check AS
SELECT a.id, a.number, a.balance,
       coalesce(sum(CASE WHEN l.direction = 'C' THEN l.amount ELSE -l.amount END), 0) AS ledger_balance
FROM account a LEFT JOIN ledger_entry l ON l.account_id = a.id
GROUP BY a.id, a.number, a.balance;
```

Run `flyway info` before and after `migrate`, commit both files with the code that uses them, and the next person (or CI) gets the identical schema with one command.

## Common mistakes

- Editing a migration that has already run in a shared environment.
- Running `flyway clean` anywhere near production (keep `cleanDisabled` true).
- One-step renames or type changes during rolling deploys.
- Huge data updates inside a schema migration.
- `CREATE INDEX` without `CONCURRENTLY` on a hot table.
- Version-number collisions between feature branches.
- Using Hibernate's `ddl-auto=update` instead of migrations (topic 4.4 explains why).

## Practice

1. Turn your topic 3.1 schema into Flyway migrations `V1`–`V4` and run them with the Flyway Docker image. Run `info`, then edit `V2` and observe the checksum error. Revert and fix properly.
2. Add a repeatable migration with a reporting view, change it, and show it re-runs.
3. Rewrite `V3` as a Liquibase formatted-SQL changeset with a rollback, run it with the Liquibase Docker image, then roll it back.
4. Plan, on paper, an expand-and-contract migration that changes `txn.amount` from `NUMERIC(19,2)` to `BIGINT` kobo. List each release and what the application does in each.

## Key terms

Migration, schema version, history table, versioned migration, repeatable migration, checksum, `migrate`, `info`, `validate`, `repair`, `baseline`, `clean`, transactional DDL, changelog, changeset, precondition, rollback, expand and contract, rolling deploy, backfill, `lock_timeout`.

## Further reading

- [Flyway documentation: Migrations](https://documentation.red-gate.com/fd/migrations-271585107.html)
- [Spring Boot: Execute Flyway database migrations on startup](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Liquibase documentation: Get started](https://docs.liquibase.com/start/home.html)
- [Martin Fowler: Evolutionary Database Design](https://martinfowler.com/articles/evodb.html)
- [Martin Fowler: Parallel Change (expand and contract)](https://martinfowler.com/bliki/ParallelChange.html)
