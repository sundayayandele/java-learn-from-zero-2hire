## What you will be able to do

- Explain what a B-tree index is and why it turns a scan of millions of rows into a few page reads.
- Design single-column, composite, covering, partial and expression indexes, and order composite columns correctly.
- Read `EXPLAIN` and `EXPLAIN ANALYZE` output in PostgreSQL and `EXPLAIN` in MySQL, and spot the expensive step.
- Recognise queries that cannot use an index ("non-sargable" predicates) and rewrite them.
- Weigh the costs of indexes: slower writes, storage and maintenance.

## Why this matters in a bank or fintech

A transaction table grows by millions of rows a day. The query behind "show my last 20 transactions" runs on every app open, thousands of times a second. Without the right index it reads the whole table each time, and the database CPU hits 100% at 9 a.m. on salary day. With the right index it reads a handful of pages in under a millisecond. Query tuning is one of the most valuable skills a backend engineer brings to a bank, and "how would you speed up this slow query?" is a near-certain interview question.

## 1. What an index is

Without an index, the database must read every row to find matches: a **sequential scan**. On 50 million transactions that means gigabytes of reads per query.

A **B-tree index** keeps a sorted copy of the indexed column(s) with pointers back to the table rows, organised as a balanced tree. Finding a value takes O(log n) page reads: a B-tree over 50 million rows is typically 3–4 levels deep. It also supports range queries (`BETWEEN`, `<`, `>`), `ORDER BY` and prefix `LIKE 'abc%'`, because entries are sorted.

```sql
CREATE INDEX idx_txn_account ON txn (account_id);
```

Primary keys and `UNIQUE` constraints create indexes automatically. **Foreign keys do not**, in PostgreSQL: index them yourself, or every join and every delete on the parent table scans the child.

Other index types in PostgreSQL: **Hash** (equality only), **GIN** (arrays, `JSONB`, full-text search), **GiST** (geometric data, ranges), **BRIN** (very large, naturally ordered tables, such as an append-only log by date: tiny and cheap). B-tree is the default and right 90% of the time.

## 2. Reading query plans in PostgreSQL

```sql
EXPLAIN ANALYZE
SELECT * FROM txn WHERE account_id = 42 ORDER BY posted_at DESC LIMIT 20;
```

Before indexing:

```text
Limit  (cost=812345.10..812345.15 rows=20 width=96) (actual time=2103.551..2103.557 rows=20 loops=1)
  ->  Sort  (cost=812345.10..812380.00 rows=13960 width=96) (actual time=2103.549..2103.552 rows=20 loops=1)
        Sort Key: posted_at DESC
        Sort Method: top-N heapsort  Memory: 30kB
        ->  Seq Scan on txn  (cost=0.00..811973.00 rows=13960 width=96) (actual time=0.031..2098.112 rows=14120 loops=1)
              Filter: (account_id = 42)
              Rows Removed by Filter: 49985880
Planning Time: 0.110 ms
Execution Time: 2103.600 ms
```

How to read it:

- It is a **tree**; read from the most indented node up. Each node feeds its parent.
- `cost=start..total` is the planner's *estimate* in arbitrary units. `actual time` (ms), `rows` and `loops` come from **actually running** the query (`ANALYZE`).
- Compare **estimated rows vs actual rows**. Big differences mean stale statistics: run `ANALYZE txn;`.
- Look for the node with the most time. Here: `Seq Scan` with `Rows Removed by Filter: 49,985,880`. It read 50 million rows to keep 14,120.

`EXPLAIN (ANALYZE, BUFFERS)` adds how many 8 KB pages were read from cache (`shared hit`) and from disk (`read`), often the clearest measure of work. Careful: `EXPLAIN ANALYZE` really executes the statement, so wrap `UPDATE`/`DELETE` in `BEGIN; ... ROLLBACK;`.

Now add the right index:

```sql
CREATE INDEX idx_txn_account_posted ON txn (account_id, posted_at DESC);
```

```text
Limit  (cost=0.56..21.78 rows=20 width=96) (actual time=0.041..0.067 rows=20 loops=1)
  ->  Index Scan using idx_txn_account_posted on txn  (cost=0.56..14812.40 rows=13960 width=96) (actual time=0.040..0.063 rows=20 loops=1)
        Index Cond: (account_id = 42)
Execution Time: 0.090 ms
```

From 2.1 seconds to 0.09 ms, more than 20,000 times faster. The index matches the filter **and** the sort order, so PostgreSQL reads the first 20 entries for account 42 and stops. No sort step at all.

### Scan types you will see

| Node | Meaning |
| --- | --- |
| `Seq Scan` | Reads the whole table. Fine for small tables or when most rows match |
| `Index Scan` | Walks the index, fetches each matching row from the table |
| `Index Only Scan` | All needed columns are in the index: no table access (needs a recently vacuumed table) |
| `Bitmap Index Scan` + `Bitmap Heap Scan` | Collects matching row locations, then reads table pages in order; good for medium selectivity or combining indexes |
| `Nested Loop` | For each outer row, look up inner rows: great when the outer side is small and the inner side is indexed |
| `Hash Join` | Builds a hash table of one side: good for large unsorted joins |
| `Merge Join` | Merges two sorted inputs |
| `Sort`, `HashAggregate`, `GroupAggregate` | Sorting and grouping; watch for `external merge Disk` (spilled to disk: raise `work_mem` or avoid the sort) |

A sequential scan is not always bad. If a query returns 40% of a table, reading it sequentially is cheaper than millions of random index lookups, and the planner knows it. **Selectivity** (what fraction of rows match) decides.

## 3. Composite indexes and column order

A composite index on `(a, b, c)` is sorted by `a`, then `b` within each `a`, then `c`. It can serve:

- `WHERE a = ?`
- `WHERE a = ? AND b = ?`
- `WHERE a = ? AND b > ?` (range on the last used column)
- `WHERE a = ? ORDER BY b`

but **not** efficiently `WHERE b = ?` alone (the **leftmost-prefix rule**, like looking up a phone book by first name).

Rules of thumb for column order:

1. Columns compared with **equality** first.
2. Then the column used for a **range** or **sort**.
3. Anything after a range column helps only as extra data (for index-only scans), not for seeking.

```sql
-- Query: an account's debits in a date range
SELECT ... FROM txn WHERE account_id = ? AND direction = 'D' AND value_date BETWEEN ? AND ?;
-- Good:  (account_id, direction, value_date)
-- Worse: (value_date, account_id)  -- range first: scans every account's rows in the date range
```

### Covering, partial and expression indexes

```sql
-- Covering: INCLUDE extra columns so the query never touches the table (index-only scan)
CREATE INDEX idx_txn_stmt ON txn (account_id, posted_at DESC) INCLUDE (amount, direction, narration);

-- Partial: index only the rows you query. Small and fast.
CREATE INDEX idx_payment_pending ON payment (created_at) WHERE status = 'PENDING';

-- Expression: index the expression you filter on
CREATE INDEX idx_customer_email_lower ON customer (lower(email));
SELECT * FROM customer WHERE lower(email) = lower(:email);
```

A partial index on pending payments stays tiny even when the table holds years of completed ones. That is perfect for the job queue in topic 3.2.

## 4. Queries that can't use indexes

An index on `x` helps only if the predicate compares `x` itself to something. These defeat it:

| Non-sargable | Rewrite |
| --- | --- |
| `WHERE date(posted_at) = '2026-10-21'` | `WHERE posted_at >= '2026-10-21' AND posted_at < '2026-10-22'` |
| `WHERE lower(email) = ?` (with an index on `email`) | Expression index on `lower(email)`, or store normalised |
| `WHERE amount * 100 > 500000` | `WHERE amount > 5000` |
| `WHERE narration LIKE '%POS%'` | Leading wildcard: use a trigram (`pg_trgm`) GIN index or full-text search |
| `WHERE account_number = 123456789` on a `VARCHAR` column | Compare with a string: implicit casts can stop index use (especially in MySQL) |
| `WHERE status <> 'CLOSED'` matching 95% of rows | Not an index problem: low selectivity. Reconsider the query |
| `WHERE a = ? OR b = ?` | Separate indexes on `a` and `b` (bitmap OR), or `UNION` |

Also: the planner relies on **statistics**. After a big data load, run `ANALYZE`. Autovacuum normally keeps statistics fresh.

## 5. The cost of indexes

Indexes are not free:

- **Every `INSERT`/`UPDATE`/`DELETE` must update every index** on the table. A transaction table with 12 indexes ingests far more slowly than one with 4.
- They take disk and memory, competing with table data for the cache.
- Unused and duplicate indexes are pure cost. Find them:

```sql
SELECT relname AS table, indexrelname AS index, idx_scan, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;
```

- On a busy production table, build indexes with `CREATE INDEX CONCURRENTLY` so writes are not blocked (it takes longer and cannot run inside a transaction block; topic 3.4 covers this in migrations).

Index for the queries you actually run. The `pg_stat_statements` extension shows the most expensive queries across the whole database, which is the right place to start tuning.

## 6. MySQL's EXPLAIN

MySQL shows a table rather than a tree:

```sql
EXPLAIN SELECT * FROM txn WHERE account_id = 42 ORDER BY posted_at DESC LIMIT 20;
```

| Column | What to look for |
| --- | --- |
| `type` | Access method, best to worst: `const`, `eq_ref`, `ref`, `range`, `index` (full index scan), **`ALL` (full table scan)** |
| `possible_keys` / `key` | Indexes considered / chosen |
| `rows` | Estimated rows examined |
| `Extra` | `Using index` (covering: good), `Using where`, **`Using filesort`** (extra sort step), **`Using temporary`** |

MySQL 8 also offers `EXPLAIN ANALYZE` (actual timings, tree format) and `EXPLAIN FORMAT=TREE`. In InnoDB the table itself is stored in primary-key order (a **clustered index**), and secondary indexes store the primary key as their row pointer, so keep primary keys short.

## Worked example: tuning the dormant-accounts report

```sql
-- Accounts with no customer-initiated transaction in 180 days: 9 minutes in production
SELECT a.number
FROM account a
WHERE a.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM txn t
      WHERE t.account_id = a.id
        AND t.channel <> 'SYSTEM'
        AND date(t.posted_at) > current_date - 180);
```

`EXPLAIN ANALYZE` shows a `Seq Scan` on `txn` inside the anti-join, run once per active account. Fixes:

1. Make the date predicate sargable: `t.posted_at > now() - interval '180 days'`.
2. Add an index matching the correlated lookup: `CREATE INDEX CONCURRENTLY idx_txn_acct_posted ON txn (account_id, posted_at) WHERE channel <> 'SYSTEM';`
3. Re-run `EXPLAIN ANALYZE`: the plan now shows an `Index Only Scan` inside a `Nested Loop Anti Join`, and the report runs in seconds.

Write the before and after plans into your pull request description. Reviewers trust numbers.

## Common mistakes

- No index on foreign key columns.
- Composite index columns in the wrong order (range column first).
- Functions or casts on indexed columns in `WHERE`.
- Adding an index for every column "just in case".
- Trusting `EXPLAIN` estimates without running `EXPLAIN ANALYZE`.
- Running `EXPLAIN ANALYZE` on a `DELETE` outside a rolled-back transaction.
- `CREATE INDEX` (not `CONCURRENTLY`) on a hot production table.

## Practice

1. Load 5–10 million rows into `txn` with `generate_series`. Time "last 20 transactions for an account" with no index, an index on `account_id`, and an index on `(account_id, posted_at DESC)`. Save the three plans.
2. Write a query that filters on `date(posted_at)`, show it can't use the index, then rewrite it.
3. Create a partial index for `status = 'PENDING'` on a payment table where 0.1% of rows are pending. Compare its size with a full index on `status`.
4. In MySQL 8 (Docker image `mysql:8.4`), run the same statement query, read `EXPLAIN`, and identify `type`, `key` and `Extra` before and after indexing.

## Key terms

Sequential scan, B-tree, index scan, index-only scan, bitmap scan, selectivity, cost, `EXPLAIN ANALYZE`, `BUFFERS`, statistics, `ANALYZE`, composite index, leftmost prefix, covering index, `INCLUDE`, partial index, expression index, sargable, nested loop, hash join, merge join, filesort, clustered index, `CREATE INDEX CONCURRENTLY`, `pg_stat_statements`.

## Further reading

- [PostgreSQL: Using EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html)
- [PostgreSQL: Indexes](https://www.postgresql.org/docs/17/indexes.html)
- [MySQL 8.4: EXPLAIN Output Format](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html)
- [Use The Index, Luke! (free SQL indexing book)](https://use-the-index-luke.com/)
- [PostgreSQL: pg_stat_statements](https://www.postgresql.org/docs/17/pgstatstatements.html)
