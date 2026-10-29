## What you will be able to do

- Connect to PostgreSQL from Java with JDBC and explain the roles of `DriverManager`, `DataSource`, `Connection`, `PreparedStatement` and `ResultSet`.
- Write safe, parameterised queries and explain how SQL injection works and why `PreparedStatement` stops it.
- Map rows to Java records, including `NUMERIC` → `BigDecimal` and `TIMESTAMPTZ` → `Instant`/`OffsetDateTime`.
- Control transactions from JDBC, handle `SQLException`s by SQLSTATE, and batch inserts efficiently.
- Configure a connection pool (HikariCP) and explain why every production app uses one.

## Why this matters in a bank or fintech

Every Java persistence tool, including JPA, Hibernate, Spring Data, jOOQ and MyBatis, sits on top of JDBC. When something goes wrong underneath them (a leaked connection, a pool timeout, a slow batch insert, a transaction that didn't commit), you can only debug it if you understand the layer below. Also, SQL injection remains one of the most common and damaging vulnerabilities in financial applications; this topic makes you immune to it.

## 1. The moving parts

```text
Your code ──> JDBC API (java.sql, javax.sql) ──> JDBC driver (org.postgresql) ──> PostgreSQL over TCP
```

- **Driver**: the vendor library that speaks the database's network protocol. Add it as a `runtime` dependency: `org.postgresql:postgresql`.
- **`DataSource`**: a factory for connections, configured once (URL, user, password, pool settings). Prefer it to `DriverManager`.
- **`Connection`**: one session with the database. Expensive to open (TCP, TLS, authentication, backend process). Not thread-safe: one thread at a time.
- **`PreparedStatement`**: a precompiled SQL statement with `?` placeholders.
- **`ResultSet`**: a cursor over the rows a query returns.

JDBC URL format: `jdbc:postgresql://host:5432/bank?sslmode=require`.

## 2. A first query

```java
import java.sql.*;

public record AccountRow(long id, String number, BigDecimal balance, String currency) { }

public Optional<AccountRow> findByNumber(String number) throws SQLException {
    String sql = """
            SELECT id, number, balance, currency
            FROM account
            WHERE number = ?
            """;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, number);                          // parameters are 1-based
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();      // rs starts BEFORE the first row
            return Optional.of(new AccountRow(
                    rs.getLong("id"),
                    rs.getString("number"),
                    rs.getBigDecimal("balance"),          // NUMERIC → BigDecimal, exact
                    rs.getString("currency")));
        }
    }
}
```

The essentials:

- **try-with-resources** for `Connection`, `PreparedStatement` and `ResultSet` (topic 1.9). With a pool, `close()` returns the connection to the pool rather than closing it, and forgetting it leaks a pooled connection.
- `executeQuery()` for `SELECT`; `executeUpdate()` for `INSERT`/`UPDATE`/`DELETE`, returning the **affected row count**; `execute()` for anything.
- `rs.next()` moves to the next row and returns `false` at the end.
- `getLong`/`getInt` return `0` for SQL `NULL`; check `rs.wasNull()` or use `rs.getObject("col", Long.class)`, which returns `null`.

### Type mapping

| PostgreSQL | Java | JDBC getter |
| --- | --- | --- |
| `BIGINT` | `long` / `Long` | `getLong` / `getObject(col, Long.class)` |
| `NUMERIC` | `BigDecimal` | `getBigDecimal` |
| `VARCHAR`, `CHAR`, `TEXT` | `String` | `getString` |
| `BOOLEAN` | `boolean` | `getBoolean` |
| `DATE` | `LocalDate` | `getObject(col, LocalDate.class)` |
| `TIMESTAMPTZ` | `OffsetDateTime` (or `Instant`) | `getObject(col, OffsetDateTime.class)` then `.toInstant()` |
| `UUID` | `UUID` | `getObject(col, UUID.class)` |
| `JSONB` | `String` (then parse) | `getString` |

Use the `java.time` types with `getObject`/`setObject` (JDBC 4.2). Avoid the legacy `java.sql.Date`/`Timestamp` in new code.

## 3. SQL injection, and why `?` placeholders stop it

Never build SQL by concatenating input:

```java
// VULNERABLE
String sql = "SELECT * FROM account WHERE number = '" + number + "'";
```

If an attacker sends `number = "' OR '1'='1"`, the database receives:

```sql
SELECT * FROM account WHERE number = '' OR '1'='1'
```

which returns every account. Worse inputs can modify or delete data, or read other tables with `UNION SELECT`.

With a `PreparedStatement`, the SQL text and the parameter values travel **separately**. The database parses the statement with a placeholder first, then binds the value as data; it can never be interpreted as SQL. `' OR '1'='1` is simply an account number that matches nothing.

Placeholders only work for **values**. Table names, column names and `ORDER BY` directions cannot be parameters. When those come from user input (a "sort by" column in an API), validate them against an allow-list:

```java
private static final Map<String, String> SORTABLE = Map.of(
        "date", "posted_at", "amount", "amount");

String column = Optional.ofNullable(SORTABLE.get(sortParam))
        .orElseThrow(() -> new IllegalArgumentException("Unsupported sort: " + sortParam));
String sql = "SELECT ... FROM txn WHERE account_id = ? ORDER BY " + column + " DESC LIMIT ?";
```

SQL injection is #3 ("Injection") in the OWASP Top 10 (2021), and Module 6 returns to it.

## 4. Writes, generated keys and transactions

```java
public long insertTxn(Connection conn, long accountId, String ref, char dir, BigDecimal amount,
                      LocalDate valueDate) throws SQLException {
    String sql = """
            INSERT INTO txn (account_id, reference, direction, amount, value_date)
            VALUES (?, ?, ?, ?, ?)
            """;
    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setLong(1, accountId);
        ps.setString(2, ref);
        ps.setString(3, String.valueOf(dir));
        ps.setBigDecimal(4, amount);
        ps.setObject(5, valueDate);
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}
```

(In PostgreSQL you can also write `INSERT ... RETURNING id` and call `executeQuery()`.)

By default a JDBC connection is in **auto-commit** mode: every statement commits immediately. For a multi-statement unit of work, switch it off and commit or roll back yourself:

```java
public void transfer(long fromId, long toId, BigDecimal amount, String ref) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            int debited = update(conn, """
                    UPDATE account SET balance = balance - ?
                    WHERE id = ? AND balance >= ?
                    """, amount, fromId, amount);
            if (debited != 1) throw new InsufficientFundsException(fromId, amount);
            update(conn, "UPDATE account SET balance = balance + ? WHERE id = ?", amount, toId);
            insertLedgerEntries(conn, ref, fromId, toId, amount);
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        }
    }   // with a pool, Hikari resets auto-commit when the connection is returned
}
```

Spring's `@Transactional` (Module 4) does exactly this for you: get a connection, disable auto-commit, run your code, commit or roll back. Knowing the JDBC version makes `@Transactional` behaviour easy to reason about.

`conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE)` sets the isolation level from topic 3.2.

## 5. Handling SQLException

`SQLException` carries a vendor-independent **SQLSTATE** code, which is better to switch on than message text:

| SQLSTATE | Meaning | Typical action |
| --- | --- | --- |
| `23505` | Unique violation | Duplicate reference: treat as "already processed" (idempotency) |
| `23503` | Foreign key violation | Referenced account doesn't exist: client error |
| `23514` | Check violation | Business rule in the schema (e.g. negative balance) |
| `40001` | Serialization failure | Retry the transaction |
| `40P01` | Deadlock detected | Retry the transaction |
| `08xxx` | Connection exception | Database unreachable: fail fast, alert |
| `57014` | Query cancelled (statement timeout) | Investigate the slow query |

```java
catch (SQLException e) {
    if ("23505".equals(e.getSQLState())) {
        return TransferResult.duplicate(ref);
    }
    throw new LedgerException("Failed to post transfer " + ref, e);   // keep the cause
}
```

Spring translates these into a `DataAccessException` hierarchy (`DuplicateKeyException`, `CannotAcquireLockException`, and so on), so you will rarely see raw `SQLException` in Spring code.

## 6. Batching

Inserting 100,000 rows one statement at a time means 100,000 network round trips. Batching sends many in one go:

```java
String sql = "INSERT INTO txn (account_id, reference, direction, amount, value_date) VALUES (?, ?, ?, ?, ?)";
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    conn.setAutoCommit(false);
    int count = 0;
    for (TxnRow r : rows) {
        ps.setLong(1, r.accountId());
        ps.setString(2, r.reference());
        ps.setString(3, r.direction());
        ps.setBigDecimal(4, r.amount());
        ps.setObject(5, r.valueDate());
        ps.addBatch();
        if (++count % 1_000 == 0) ps.executeBatch();   // flush every 1,000 rows
    }
    ps.executeBatch();
    conn.commit();
}
```

With the PostgreSQL driver, add `reWriteBatchedInserts=true` to the JDBC URL so it rewrites the batch into multi-row `INSERT`s: often another 2–5× faster. For very large loads, PostgreSQL's `COPY` (available through the driver's `CopyManager`) is fastest of all.

## 7. Connection pooling with HikariCP

Opening a PostgreSQL connection takes several milliseconds and creates a server process. A **connection pool** keeps a set of open connections and lends them out. HikariCP is the standard (and Spring Boot's default).

```java
HikariConfig cfg = new HikariConfig();
cfg.setJdbcUrl("jdbc:postgresql://localhost:5432/bank?reWriteBatchedInserts=true");
cfg.setUsername("bank_app");
cfg.setPassword(System.getenv("DB_PASSWORD"));     // never hard-code secrets (topic 4.5)
cfg.setMaximumPoolSize(10);
cfg.setConnectionTimeout(3_000);                   // ms to wait for a free connection before failing
cfg.setLeakDetectionThreshold(20_000);             // log a stack trace if a connection is held > 20 s
cfg.setPoolName("bank-pool");
HikariDataSource dataSource = new HikariDataSource(cfg);
```

How big? Smaller than people expect. A database server can only do a limited number of things at once (roughly its CPU cores plus some for I/O wait). HikariCP's guidance starts from `connections ≈ (cores × 2) + effective spindles`, so a pool of 10–20 per service instance is typical. Remember to multiply by the number of instances: 8 pods × 50 connections = 400 PostgreSQL backends, which is too many. If threads wait for connections, the fix is usually faster queries, not a bigger pool.

Symptoms of trouble: `Connection is not available, request timed out after 3000ms` means the pool is exhausted: connections are leaking (missing `close`) or held too long (slow queries, transactions spanning remote calls).

## 8. Beyond plain JDBC

Plain JDBC is verbose. You will use higher-level tools, all built on it:

- **Spring `JdbcClient`** (Spring 6.1+): fluent, plain-SQL, no boilerplate. Excellent for reporting queries and simple services.

  ```java
  Optional<AccountRow> acc = jdbcClient.sql("SELECT id, number, balance, currency FROM account WHERE number = :n")
          .param("n", number)
          .query(AccountRow.class)       // maps columns to record components by name
          .optional();
  ```

- **Spring Data JDBC / JPA with Hibernate** (topic 4.4): map objects to tables.
- **jOOQ**: type-safe SQL generated from your schema.
- **MyBatis**: SQL in XML or annotations, common in some banks.

## Worked example: a statement importer

A partner bank sends a daily CSV of 250,000 settlement lines. The importer reads it, validates each line, batch-inserts valid lines in one transaction, and records rejects:

```java
public ImportReport importFile(Path file) throws IOException, SQLException {
    var rejects = new ArrayList<String>();
    try (var reader = Files.newBufferedReader(file);
         var conn = dataSource.getConnection();
         var ps = conn.prepareStatement("""
                 INSERT INTO switch_settlement (reference, amount, value_date)
                 VALUES (?, ?, ?)
                 ON CONFLICT (reference) DO NOTHING
                 """)) {
        conn.setAutoCommit(false);
        String line; int lineNo = 0, batched = 0;
        reader.readLine();                                   // skip header
        while ((line = reader.readLine()) != null) {
            lineNo++;
            var cols = line.split(",", -1);
            try {
                ps.setString(1, cols[0].strip());
                ps.setBigDecimal(2, new BigDecimal(cols[1].strip()));
                ps.setObject(3, LocalDate.parse(cols[2].strip()));
                ps.addBatch();
                if (++batched % 1_000 == 0) ps.executeBatch();
            } catch (RuntimeException e) {
                rejects.add("line " + lineNo + ": " + e.getMessage());
            }
        }
        ps.executeBatch();
        conn.commit();
        return new ImportReport(lineNo, rejects);
    }
}
```

`ON CONFLICT (reference) DO NOTHING` makes the import **idempotent**: re-running the same file inserts nothing new. That property is what lets operations safely retry a failed nightly job.

## Common mistakes

- Concatenating user input into SQL.
- Forgetting to close connections, statements or result sets.
- Calling `getLong` on a nullable column and silently getting 0.
- Auto-commit left on for multi-statement work.
- Not checking `executeUpdate()`'s affected row count.
- Huge pools "to be safe", multiplied across many instances.
- Inserting large files row by row without batching.
- Using `double` (`getDouble`) for `NUMERIC` money columns.

## Practice

1. Write `AccountDao` with `findByNumber`, `insert`, and `listByCustomer` using plain JDBC and a HikariCP `DataSource`. Test it against PostgreSQL in Docker.
2. Prove SQL injection: write a deliberately vulnerable login-style query, exploit it with `' OR '1'='1`, then fix it with a `PreparedStatement` and show the attack fails.
3. Insert 200,000 rows three ways (one by one with auto-commit, batched, batched with `reWriteBatchedInserts=true`) and record the timings.
4. Implement `transfer` with manual transaction control, then force a failure after the debit (throw an exception) and prove the debit was rolled back.

## Key terms

JDBC, driver, `DataSource`, `DriverManager`, `Connection`, `PreparedStatement`, `ResultSet`, placeholder, SQL injection, allow-list, auto-commit, commit, rollback, generated keys, `RETURNING`, SQLSTATE, batch, `reWriteBatchedInserts`, `COPY`, connection pool, HikariCP, leak detection, `JdbcClient`, idempotent import.

## Further reading

- [The Java Tutorials: JDBC Database Access](https://docs.oracle.com/javase/tutorial/jdbc/basics/index.html)
- [PostgreSQL JDBC driver documentation](https://jdbc.postgresql.org/documentation/)
- [HikariCP: About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [OWASP: SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [PostgreSQL: Error codes (SQLSTATE)](https://www.postgresql.org/docs/17/errcodes-appendix.html)
- [Oracle: java.sql package summary (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/package-summary.html)
