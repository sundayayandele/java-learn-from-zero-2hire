## What you will be able to do

- Explain when a non-relational store is the right tool, and when PostgreSQL is still the better choice.
- Use Redis data structures (strings, hashes, lists, sets, sorted sets) and TTLs from the CLI and from Java.
- Implement cache-aside caching with sensible keys, expiry and invalidation, and avoid stampedes and stale data.
- Use Redis for rate limiting, idempotency keys and short-lived tokens.
- Model documents in MongoDB, run queries and aggregations, create indexes, and decide between embedding and referencing.

## Why this matters in a bank or fintech

The source of truth for money stays in a relational database with ACID transactions (topics 3.1–3.2). Around it, banks and fintechs use Redis for things that must be fast and are allowed to expire: cached FX rates and account summaries, OTPs, session data, rate limits on login attempts and transfer APIs, and idempotency keys that stop a retried payment from posting twice. MongoDB appears where data is naturally document-shaped and varies a lot: KYC documents and onboarding journeys, customer-support case histories, audit trails of API payloads. Job ads from Lagos fintechs and international banks list both. The skill they are testing is judgement: which store for which data.

## 1. Choosing a store

| Need | Good fit |
| --- | --- |
| Balances, ledgers, transfers: anything needing multi-row ACID and constraints | **PostgreSQL / MySQL** |
| Sub-millisecond reads of hot data, counters, expiring keys, rate limits | **Redis** |
| Flexible, nested, evolving records read as a whole (KYC file, onboarding state, case history) | **MongoDB** (or PostgreSQL `JSONB` for modest needs) |
| Full-text search over narrations and documents | Elasticsearch/OpenSearch, or PostgreSQL full-text |
| Analytics over billions of rows | A columnar warehouse (BigQuery, Redshift, ClickHouse) |

"NoSQL" is not one thing: key-value (Redis), document (MongoDB), wide-column (Cassandra), graph (Neo4j). They trade some relational guarantees (joins, multi-table transactions, rigid schemas) for scale, speed or flexibility. Many teams now start with PostgreSQL for everything and add a specialised store only when there is a measured need.

## 2. Redis basics

Redis is an in-memory data-structure server. Every operation on a single key is atomic, and commands run one at a time (single-threaded execution), which makes many concurrency problems simple.

```bash
docker run -d --name redis -p 6379:6379 redis:8-alpine
docker exec -it redis redis-cli
```

| Type | Commands | Banking use |
| --- | --- | --- |
| String | `SET`, `GET`, `INCR`, `SET key value EX 300 NX` | Cached JSON, counters, OTPs, locks |
| Hash | `HSET`, `HGET`, `HGETALL`, `HINCRBY` | Account summary fields |
| List | `LPUSH`, `RPOP`, `LRANGE` | Simple queues, recent-activity feeds |
| Set | `SADD`, `SISMEMBER`, `SMEMBERS` | Blocked device IDs, feature flags per segment |
| Sorted set | `ZADD`, `ZRANGEBYSCORE`, `ZREMRANGEBYSCORE` | Sliding-window rate limits, leaderboards, scheduled jobs |
| Stream | `XADD`, `XREADGROUP` | Lightweight event log (Kafka is covered in Module 11) |

```text
SET fx:USD:NGN "1532.40" EX 300        # expires in 300 seconds
GET fx:USD:NGN
TTL fx:USD:NGN                          # seconds left
INCR login:fail:0123456789              # atomic increment
EXPIRE login:fail:0123456789 900
SET otp:txn-8841 "482913" EX 120 NX     # only set if absent: one OTP per transaction
HSET acct:0123456789 balance 150000.00 currency NGN status ACTIVE
HGETALL acct:0123456789
```

**Key naming**: use a consistent namespace such as `service:entity:id[:field]` (`wallet:balance:0123456789`). Never put secrets or full PII in key names, since they appear in monitoring tools.

**Persistence**: Redis can snapshot (RDB) and log writes (AOF) to disk, but treat it as a cache unless you have deliberately configured it as a durable store. Plan for "Redis restarted and is empty".

## 3. Caching patterns

### Cache-aside (lazy loading)

The application owns the logic:

1. Look in the cache.
2. On a **hit**, return it.
3. On a **miss**, read from the database, write it to the cache with a TTL, and return it.
4. On **update**, write to the database, then **delete** (not update) the cache key.

```java
public AccountSummary summary(String number) {
    String key = "acct:summary:" + number;
    String cached = redis.opsForValue().get(key);
    if (cached != null) return json.readValue(cached, AccountSummary.class);

    AccountSummary fresh = repository.loadSummary(number);          // database: source of truth
    redis.opsForValue().set(key, json.writeValueAsString(fresh), Duration.ofSeconds(60));
    return fresh;
}

public void onTransactionPosted(String number) {
    redis.delete("acct:summary:" + number);                          // invalidate after the DB commit
}
```

Deleting rather than updating avoids races where two writers put values into the cache in the wrong order. Spring's `@Cacheable`/`@CacheEvict` implement this pattern declaratively (you will use them in Module 4 and Module 13).

### What to cache, and for how long

- Good candidates: data read far more than it changes, and where slightly stale is acceptable: FX reference rates, bank lists, product catalogues, fee schedules, account summaries for a dashboard.
- **Do not** serve balances used for authorising payments from a cache. Authorisation must read the source of truth inside the transaction (topic 3.2).
- Every entry gets a **TTL**. A TTL is your safety net when invalidation is missed.
- Add a little **random jitter** to TTLs so thousands of keys don't expire at the same second.

### Problems to recognise

- **Cache stampede**: a hot key expires and 5,000 requests hit the database at once. Mitigations: jittered TTLs, a short lock so only one request recomputes (`SET lock:key 1 NX EX 5`), or refreshing popular keys ahead of expiry.
- **Stale data**: invalidation missed, or done before the database commit. Invalidate *after* commit.
- **Cache penetration**: repeated lookups for keys that don't exist (often an attack) always miss. Cache "not found" briefly too.
- **Memory pressure**: configure `maxmemory` and an eviction policy (`allkeys-lru` for pure caches).

## 4. Redis beyond caching

### Rate limiting

A fixed-window limit of 5 failed PIN attempts per 15 minutes:

```text
INCR pin:fail:0123456789
EXPIRE pin:fail:0123456789 900 NX      # set the window only on the first failure
```

If the counter exceeds 5, block. A **sliding window** uses a sorted set of timestamps: `ZADD` the current time, `ZREMRANGEBYSCORE` anything older than the window, `ZCARD` to count, all in one `MULTI`/`EXEC` transaction or a Lua script so the steps are atomic.

### Idempotency keys

A client sends `Idempotency-Key: 7f3c...` with a payment. The server records it before processing:

```text
SET idem:7f3c... "IN_PROGRESS" NX EX 86400
```

If `SET ... NX` fails, the key exists: this is a retry, so return the stored result instead of paying twice. Module 5 builds this properly. (The database `UNIQUE` reference from topic 3.1 remains the final guarantee.)

### Distributed locks, with caution

`SET lock:eod-job <instanceId> NX EX 600` lets one instance run the end-of-day job. A lock with an expiry can be lost if the holder pauses longer than the TTL, so for correctness-critical work prefer database locks (advisory locks or `SELECT ... FOR UPDATE`, topic 3.2).

## 5. Redis from Java

With Spring Data Redis (Spring Boot starter `spring-boot-starter-data-redis`, which uses the Lettuce client):

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

```java
@Service
public class FxRateCache {
    private final StringRedisTemplate redis;
    public FxRateCache(StringRedisTemplate redis) { this.redis = redis; }

    public Optional<BigDecimal> get(String base, String quote) {
        return Optional.ofNullable(redis.opsForValue().get("fx:" + base + ":" + quote)).map(BigDecimal::new);
    }

    public void put(String base, String quote, BigDecimal rate) {
        long jitter = ThreadLocalRandom.current().nextLong(0, 30);
        redis.opsForValue().set("fx:" + base + ":" + quote, rate.toPlainString(), Duration.ofSeconds(300 + jitter));
    }
}
```

Store `BigDecimal` as a plain string (`toPlainString()`), never as a double.

## 6. MongoDB basics

MongoDB stores **documents** (BSON, a binary JSON) in **collections**. Documents in one collection can have different shapes, and can nest objects and arrays.

```bash
docker run -d --name mongo -p 27017:27017 mongo:8
docker exec -it mongo mongosh
```

A KYC onboarding case:

```javascript
db.kyc_cases.insertOne({
  caseId: "KYC-2026-000913",
  customer: { bvn: "22212345678", fullName: "Ada Okafor", dob: ISODate("1994-03-02") },
  tier: 2,
  status: "PENDING_REVIEW",
  documents: [
    { type: "NIN_SLIP", fileRef: "s3://kyc/913/nin.pdf", verified: true },
    { type: "UTILITY_BILL", fileRef: "s3://kyc/913/bill.pdf", verified: false, rejectReason: "older than 3 months" }
  ],
  checks: { bvnMatch: true, sanctionsHit: false, pepHit: false },
  history: [
    { at: ISODate("2026-10-20T09:12:00Z"), by: "system", action: "CREATED" },
    { at: ISODate("2026-10-20T09:15:00Z"), by: "officer.bello", action: "REQUESTED_DOCUMENT" }
  ]
})
```

Queries and updates:

```javascript
db.kyc_cases.find({ status: "PENDING_REVIEW", tier: { $gte: 2 } }).sort({ "history.0.at": 1 }).limit(20)
db.kyc_cases.find({ "documents.type": "UTILITY_BILL", "documents.verified": false })   // matches inside arrays
db.kyc_cases.updateOne(
  { caseId: "KYC-2026-000913" },
  { $set: { status: "APPROVED" },
    $push: { history: { at: new Date(), by: "officer.bello", action: "APPROVED" } } })
```

Aggregation pipelines are MongoDB's `GROUP BY`:

```javascript
db.kyc_cases.aggregate([
  { $match: { "history.0.at": { $gte: ISODate("2026-10-01") } } },
  { $group: { _id: "$status", cases: { $sum: 1 } } },
  { $sort: { cases: -1 } }
])
```

**Indexes** matter exactly as in topic 3.3, and `explain("executionStats")` shows whether a query used one (`IXSCAN`) or scanned the whole collection (`COLLSCAN`):

```javascript
db.kyc_cases.createIndex({ status: 1, tier: 1 })
db.kyc_cases.createIndex({ caseId: 1 }, { unique: true })
db.kyc_cases.find({ status: "PENDING_REVIEW", tier: 2 }).explain("executionStats")
```

### Modelling: embed or reference?

- **Embed** data that is read together, owned by the parent, and bounded in size: a case's documents and checks.
- **Reference** (store an ID) when data is shared, large, unbounded or updated independently: the customer master record, or an account's transactions (which grow forever: never embed them in an account document; documents are limited to 16 MB).
- Design around your **queries**, not around normalisation. Some duplication is normal in document models, so decide which copy is authoritative.

MongoDB supports multi-document ACID transactions on replica sets, but if your design needs them constantly, the data is probably relational. Schema validation (`$jsonSchema` validators) lets you enforce required fields, which is wise for regulated data.

With Spring Boot, `spring-boot-starter-data-mongodb` gives you `MongoTemplate` and repository interfaces like Spring Data JPA's (topic 4.3). Note that Spring Boot 4 changed how `UUID` and `BigDecimal` are represented in MongoDB by default; check the migration notes before upgrading an existing app.

## Worked example: an FX-rate service with a cache

The service exposes `GET /rates/{base}/{quote}`. Rates come from the ECB daily reference file (Capstone 2), stored in PostgreSQL. The read path:

1. Check Redis `fx:{date}:{base}:{quote}`; on a hit, return (sub-millisecond).
2. On a miss, query PostgreSQL, cache with a TTL that ends shortly after the next ECB publication (~16:00 CET), plus jitter.
3. When the daily import job loads new rates, it deletes the affected keys after committing.
4. If Redis is down, log a warning and serve from PostgreSQL: the cache is an optimisation, never a dependency that can take the service down.

## Common mistakes

- Treating Redis as the source of truth for balances.
- Caching without a TTL.
- Updating the cache instead of invalidating it, or invalidating before the DB commit.
- Storing money as floating point in caches or documents.
- `KEYS *` on a production Redis (blocks the server; use `SCAN`).
- Embedding unbounded arrays in MongoDB documents.
- No indexes on MongoDB query fields (`COLLSCAN` everywhere).
- Choosing MongoDB "because it's flexible" for data that is relational and transactional.

## Practice

1. In `redis-cli`, implement a 5-attempts-per-15-minutes PIN lockout with `INCR` and `EXPIRE`, and verify the key expires.
2. Add cache-aside to a Java `AccountSummaryService` with `StringRedisTemplate`. Write a test that proves a second call doesn't hit the database, and that posting a transaction invalidates the entry.
3. Build a sliding-window rate limiter (10 transfer requests per minute per customer) with a sorted set inside `MULTI`/`EXEC`.
4. Load 1,000 KYC case documents into MongoDB, write the "oldest pending tier-2 cases" query, check it with `explain` before and after adding an index, and write an aggregation counting cases per status per day.

## Key terms

NoSQL, key-value store, document store, Redis, TTL, `NX`, atomic command, cache-aside, cache hit/miss, invalidation, jitter, cache stampede, cache penetration, eviction policy, rate limiting, idempotency key, distributed lock, MongoDB, BSON, collection, document, embedded vs referenced, aggregation pipeline, `COLLSCAN`/`IXSCAN`, schema validation.

## Further reading

- [Redis docs: Data types](https://redis.io/docs/latest/develop/data-types/)
- [Redis docs: Caching patterns (client-side and server-side)](https://redis.io/docs/latest/develop/clients/client-side-caching/)
- [Spring Data Redis reference](https://docs.spring.io/spring-data/redis/reference/)
- [MongoDB Manual: Data Modeling](https://www.mongodb.com/docs/manual/data-modeling/)
- [MongoDB Manual: Aggregation](https://www.mongodb.com/docs/manual/aggregation/)
