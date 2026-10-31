## What you will be able to do

- Choose a caching pattern (cache-aside, read-through, write-through, write-behind) and say what must never be cached.
- Cache with Spring and Redis, invalidate correctly after a transaction commits, and survive Redis being down.
- Protect a cache from stampedes and its data from going stale.
- Size and monitor a database connection pool, and find slow queries and lock waits under load.

## Why this matters in a bank or fintech

Mobile banking apps poll: "is my transfer done yet?" every second or two, from every phone with a pending transfer. On salary day that's thousands of identical reads a second hitting the database that also has to post the transfers. A cache in front of those reads takes the load off, as long as it never tells a customer something false about their money. And when the database does struggle, it's usually not "the database is slow": it's a pool that's too small for how long connections are held, one slow query, or a lock queue. Those are the things this topic teaches you to see.

## 1. Caching patterns

| Pattern | How | Fits |
| --- | --- | --- |
| **Cache-aside** | The app reads the cache; on a miss, reads the database and fills the cache | Most read-heavy data; what Spring's `@Cacheable` does |
| **Read-through** | The cache itself loads from the database on a miss | Caching libraries and platforms that support loaders |
| **Write-through** | Writes go to the cache and the database together | Data read immediately after it's written |
| **Write-behind** | Writes go to the cache, the database later | Rarely in banking: a crash loses writes |

**What never to cache for decisions:** balances used to approve a payment, limits, fraud scores. Those decisions read the source of truth in the transaction (topic 10.4). ITL Pay caches only the **status shown to the customer**, and the saga always reads the database. That rule is in the code:

```java
/**
 * The status a customer's app polls, from Redis when it's there (topic 13.4). Only for answering clients:
 * the saga always reads the database through find(), never a cache that might be a moment old.
 */
@Cacheable(cacheNames = "transfers", unless = "#result == null")
public Optional<Transfer> status(String reference) {
    return find(reference);
}
```

`unless = "#result == null"` means "not found" isn't cached, so a status read that arrives before the transfer exists doesn't hide it for 30 seconds.

## 2. Invalidation: after the commit, not before

When the saga moves a transfer, the cached status is stale. Evict it:

```java
@Transactional
@CacheEvict(cacheNames = "transfers", key = "#t.reference()")   // after the commit: the cache manager is transaction-aware
public boolean move(Transfer t, State from, State to, String event, String reason) { ... }
```

The timing matters. If the eviction happens **before** the database commits, a status read in between misses the cache, reads the **old** row (not yet committed) and puts it back in the cache, where it stays until the TTL expires. The starter's cache manager is **transaction-aware**, so evictions run after a successful commit:

```java
@Bean
RedisCacheManager cacheManager(RedisConnectionFactory redis) {
    var transfers = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(30))                    // a safety net: eviction on change does the real work
            .prefixCacheNameWith("itl-pay:")
            .serializeValuesWith(SerializationPair.fromSerializer(
                    new JacksonJsonRedisSerializer<>(Transfers.Transfer.class)));   // JSON, never Java serialisation
    return RedisCacheManager.builder(redis)
            .withCacheConfiguration("transfers", transfers)
            .transactionAware()                                  // evict after the database commits, not before
            .enableStatistics()                                  // cache.gets{result=hit|miss} in Prometheus
            .build();
}
```

Store **JSON**, not Java serialisation: it's readable in `redis-cli`, survives class changes, and avoids the security risks of deserialising bytes. A test proves the whole cycle against real Redis: read the status (cache filled), let fraud clear the transfer, check the key is gone, read `CLEARED`.

## 3. When Redis is down

A cache is an optimisation; it must never take the service down. Out of the box, Spring **propagates** cache errors, so a Redis outage would turn every status read into a 500, and Lettuce's default command timeout is **60 seconds**. The starter changes both:

```java
@Override
public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();       // log it and answer from the database
}
```

```properties
spring.data.redis.timeout=250ms
spring.data.redis.connect-timeout=250ms
```

`RedisDownTests` creates a transfer, reads its status, **stops the Redis container**, and reads it again: the answer still comes back (from PostgreSQL) in under two seconds instead of failing. Expect the database load to rise while Redis is away, which is one more reason to size the pool with headroom.

## 4. Stampedes and hot keys

When a popular key expires, every request misses at once and they all hit the database together: a **cache stampede**. Defences:

- `@Cacheable(sync = true)`: only one thread per JVM loads a missing key; the others wait for it. (Per JVM: with ten instances, up to ten loads.)
- **Jittered TTLs** so keys don't all expire at the same moment.
- **Refresh ahead** (reload before expiry) for a few very hot keys, such as exchange rates.

Measure the result: the starter exports `cache_gets_total{result="hit|miss"}`. In a light test, 5 of 6 status reads hit (83%); under the load test it fell to 62%, because each saga step evicts the key and apps poll right after creating a transfer. That's honest data for deciding whether the cache is worth it: compare database load and latency with it on and off.

And measure the cache's own cost. The JFR recording in topic 13.3 showed every Redis cache read creating a small connection wrapper that looks up a logger through a synchronized table; under 60 requests a second, threads waited there for up to 1.86 s. A cache is code on your hot path like any other.

## 5. The database under load: connection pools

A service talks to PostgreSQL through a pool (HikariCP in Spring Boot, 10 connections by default). Requests that need a connection while all are busy **wait**, which shows up as `hikaricp_connections_pending`. In ITL Pay's first load test (topic 13.5), **all 10 were in use and 18 requests were waiting**.

The tempting fix is a bigger pool. The real question is **why connections are held so long**. Here, the outbox relay held a transaction open while it waited for Kafka to acknowledge 100 events one by one. Once it sent the batch and waited once, waiting connections dropped from 18 to 3, without changing the pool size.

Rules of thumb:

- Pool size × instances must fit within PostgreSQL's `max_connections` (100 by default), leaving room for migrations, admin and other services. Six services with 10 connections each and three replicas is already 180.
- Bigger pools aren't faster beyond a point: PostgreSQL does best with a modest number of active connections (a few per CPU core). Many services use **PgBouncer** in front.
- Never call another service or a broker while holding a transaction, unless you mean to.
- Watch `hikaricp_connections_active`, `_pending` and `hikaricp_connections_usage_seconds` (how long connections are held).

## 6. Slow queries and locks

```sql
-- With the pg_stat_statements extension: the queries that use the most total time
SELECT calls, round(mean_exec_time::numeric, 2) AS mean_ms, round(total_exec_time::numeric) AS total_ms, query
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10;

-- Who is waiting for whom, right now
SELECT pid, wait_event_type, wait_event, state, now() - query_start AS running_for, query
FROM pg_stat_activity WHERE state <> 'idle' ORDER BY running_for DESC;
```

Then `EXPLAIN (ANALYZE, BUFFERS)` the worst query (topic 3.3). Patterns that show up under load:

- **A missing index** on a column you filter or join by; the outbox uses a **partial index** `ON outbox (id) WHERE published_at IS NULL`, so finding unpublished rows stays fast however big the table gets.
- **N+1 queries** from JPA lazy loading: one query per row instead of one join.
- **Lock queues**: many transactions updating the same row (a single "bank float" account updated by every transfer is a classic). Spread hot rows, or use `SKIP LOCKED` for work queues as the relay does.
- **Long transactions** holding locks and connections; set `idle_in_transaction_session_timeout`.

## Worked example: salary-day status polling

An illustrative scenario; the numbers measured on the starter are in the sections above.


1. 40,000 customers are paid at 08:00 and many send money at once. Their apps poll status every 2 seconds.
2. Without the cache, status reads are 80% of transfers' queries; the pool's waiting line climbs and p95 rises to 900 ms.
3. With cache-aside (30-second TTL, evicted after each state change), the hit ratio settles around 70%; database queries per second fall by about two-thirds and waiting connections return to zero.
4. At 08:20, Redis restarts during a patch. Status reads fall back to PostgreSQL for 40 seconds with a warning in the logs; latency rises briefly; nobody sees an error.
5. Balances and limits were never cached, so no customer was ever approved against a stale number.

## Common mistakes

- Caching data used for financial decisions.
- Evicting before the database commits, and caching the old value.
- Letting a cache outage fail requests, or 60-second timeouts to a dead Redis.
- Java serialisation in Redis.
- Caching "not found".
- Growing the connection pool instead of finding what holds connections.
- Calling other services or Kafka inside a database transaction.

## Practice

1. Run the load test with the cache, then without it (add a property that switches the cache manager to `NoOpCacheManager`), and compare transfers' database queries per second, latency and HikariCP waits.
2. Add `sync = true` to `@Cacheable` and explain in two sentences what it changes with three transfers instances.
3. Enable `pg_stat_statements` in the Compose PostgreSQL (`shared_preload_libraries`), run the load test, and list the three most expensive queries. Is any of them a surprise?
4. Make one account a "hot row" (every transfer debits A1), run the load test, and find the lock waits in `pg_stat_activity`. Propose a design that avoids it.
5. Write ITL Pay's cache policy as a short ADR section: what's cached, TTL, invalidation, behaviour when Redis is down, and what is never cached.

## Key terms

Cache-aside, read-through, write-through, write-behind, TTL, eviction, invalidation, transaction-aware cache, negative caching, cache stampede, `sync`, jitter, refresh ahead, hot key, hit ratio, `CacheErrorHandler`, Redis, Lettuce, serialisation, connection pool, HikariCP, pool exhaustion, `max_connections`, PgBouncer, `pg_stat_statements`, `pg_stat_activity`, partial index, N+1 query, lock queue, hot row, `SKIP LOCKED`.

## Further reading

- [Spring Framework reference: cache abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Spring Data Redis reference: Redis cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)
- [Redis: caching patterns](https://redis.io/solutions/caching/)
- [HikariCP: about pool sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [PostgreSQL: pg_stat_statements](https://www.postgresql.org/docs/current/pgstatstatements.html)
- [PostgreSQL: monitoring database activity](https://www.postgresql.org/docs/current/monitoring-stats.html)
