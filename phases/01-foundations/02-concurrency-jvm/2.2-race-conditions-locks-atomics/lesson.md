## What you will be able to do

- Recognise race conditions (check-then-act, read-modify-write) and reproduce one deliberately.
- Explain visibility, atomicity and ordering, and the "happens-before" rule in plain words.
- Protect shared state with `synchronized`, `ReentrantLock`, `ReadWriteLock`, `volatile` and atomic classes, and choose between them.
- Use concurrent collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`, blocking queues) correctly.
- Diagnose and prevent deadlocks, and know when to use semaphores and latches.

## Why this matters in a bank or fintech

The most expensive concurrency bug in finance is the **double spend**: two requests withdraw from the same account at the same time, both see enough balance, and both succeed. Variants of it include lost updates to a daily-limit counter, duplicated webhook processing and a reconciliation total that differs every run. These bugs pass every unit test and appear only under load, in production, usually on salary day. This topic teaches you to see them in code review before they ship.

## 1. What a race condition looks like

```java
public class Wallet {
    private BigDecimal balance = new BigDecimal("1000.00");

    public boolean withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) >= 0) {            // 1. check
            balance = balance.subtract(amount);          // 2. act
            return true;
        }
        return false;
    }
}
```

Two threads call `withdraw(800)` at the same moment:

| Time | Thread A | Thread B | balance |
| --- | --- | --- | --- |
| t1 | reads 1000, check passes | | 1000 |
| t2 | | reads 1000, check passes | 1000 |
| t3 | writes 200 | | 200 |
| t4 | | writes 200 | 200 |

₦1,600 left the wallet; the balance says ₦800 was withdrawn. This is a **check-then-act** race. Its sibling is **read-modify-write**: `count++` is really three steps (read, add one, write), so two threads can both read 41 and both write 42.

You can reproduce it reliably:

```java
var counter = new int[1];
try (var pool = Executors.newFixedThreadPool(8)) {
    for (int i = 0; i < 100_000; i++) pool.submit(() -> counter[0]++);
}
System.out.println(counter[0]);   // rarely 100000
```

A **critical section** is code that touches shared mutable state and must not be interleaved. The fixes below make critical sections atomic, or remove the shared mutable state entirely.

## 2. The three problems: atomicity, visibility, ordering

- **Atomicity**: an operation happens entirely or not at all, from other threads' point of view.
- **Visibility**: a write by one thread is seen by another. Without synchronisation, a thread may keep reading a stale value from a CPU cache or register *forever*.
- **Ordering**: compilers and CPUs reorder instructions for speed. Within one thread the result looks the same, but another thread can observe the steps in a surprising order.

The **Java Memory Model (JMM)** defines when one thread is guaranteed to see another's writes, through **happens-before** relationships. You do not need to memorise the formal rules; you need to know which tools create happens-before:

- releasing a lock happens-before the next acquisition of the same lock;
- a write to a `volatile` field happens-before every later read of it;
- `Thread.start()` happens-before anything in the started thread; everything in a thread happens-before another thread's `join()` returns;
- putting an item into a concurrent collection happens-before taking it out;
- completing a `Future` happens-before `get()` returns.

If shared mutable data is accessed without one of these, you have a data race and the behaviour is undefined.

## 3. `synchronized`

Every Java object has an intrinsic lock (monitor). `synchronized` acquires it, giving both mutual exclusion and visibility:

```java
public class Wallet {
    private BigDecimal balance = new BigDecimal("1000.00");

    public synchronized boolean withdraw(BigDecimal amount) {   // locks on `this`
        if (balance.compareTo(amount) < 0) return false;
        balance = balance.subtract(amount);
        return true;
    }

    public synchronized BigDecimal balance() { return balance; }   // reads must synchronise too
}
```

Or lock on a private object, so outside code cannot interfere with your lock:

```java
private final Object lock = new Object();

public boolean withdraw(BigDecimal amount) {
    synchronized (lock) { ... }
}
```

Rules: keep critical sections short; never call slow I/O or unknown code while holding a lock; and make **both reads and writes** of shared state go through the same lock. A synchronised writer with an unsynchronised reader is still broken.

**This only protects one JVM.** A real wallet balance lives in a database, and your service runs as several instances. There, the database provides the locking (`SELECT ... FOR UPDATE`, optimistic version columns, or a conditional `UPDATE`), which you will learn in topic 3.2. In-memory locks are for in-memory state: caches, counters, rate limiters.

## 4. Explicit locks: `ReentrantLock` and `ReadWriteLock`

`java.util.concurrent.locks` gives you locks with more control:

```java
private final ReentrantLock lock = new ReentrantLock();

public boolean withdraw(BigDecimal amount) throws InterruptedException {
    if (!lock.tryLock(200, TimeUnit.MILLISECONDS)) {     // give up instead of waiting forever
        throw new WalletBusyException(id);
    }
    try {
        if (balance.compareTo(amount) < 0) return false;
        balance = balance.subtract(amount);
        return true;
    } finally {
        lock.unlock();                                    // ALWAYS in finally
    }
}
```

Use `ReentrantLock` when you need `tryLock` with a timeout, interruptible waiting (`lockInterruptibly`), fairness, or several `Condition`s. Otherwise `synchronized` is simpler and equally fast. (Before Java 24, `synchronized` blocks pinned virtual threads to their carrier; topic 2.3 explains why that mattered.)

`ReentrantReadWriteLock` allows many concurrent readers or one writer, which suits data that is read constantly and updated rarely, such as an in-memory FX-rate table refreshed every few minutes. `StampedLock` adds optimistic reads for very hot paths.

## 5. `volatile` and atomic classes

`volatile` guarantees **visibility and ordering**, not atomicity. It is right for a flag written by one thread and read by others:

```java
private volatile boolean shuttingDown = false;

public void stop() { shuttingDown = true; }          // visible to the worker immediately
public void run() { while (!shuttingDown) { processNext(); } }
```

`volatile int count; count++;` is still a race: the increment is a read-modify-write.

The **atomic classes** perform read-modify-write operations atomically, without locks, using the CPU's compare-and-swap (CAS) instruction:

```java
private final AtomicLong processed = new AtomicLong();
processed.incrementAndGet();

private final AtomicReference<BigDecimal> balance = new AtomicReference<>(new BigDecimal("1000.00"));

public boolean withdraw(BigDecimal amount) {
    while (true) {                                       // CAS loop: retry if someone else changed it
        BigDecimal current = balance.get();
        if (current.compareTo(amount) < 0) return false;
        if (balance.compareAndSet(current, current.subtract(amount))) return true;
    }
}
```

(`compareAndSet` on an `AtomicReference` compares by reference, which works here because `BigDecimal` values are replaced, never mutated.) `updateAndGet` and `accumulateAndGet` wrap the loop for you.

For counters updated by many threads at once (requests per second, transactions processed), `LongAdder` spreads updates across cells and is much faster than `AtomicLong` under contention.

## 6. Concurrent collections

The ordinary collections from topic 1.4 are not thread-safe. `Collections.synchronizedMap(map)` wraps every method in one lock, which is correct but slow, and compound actions are still racy. Prefer `java.util.concurrent`:

| Need | Use |
| --- | --- |
| Shared map with frequent reads and writes | `ConcurrentHashMap` |
| List read very often, changed rarely (listeners, config) | `CopyOnWriteArrayList` |
| Producer/consumer hand-off | `ArrayBlockingQueue`, `LinkedBlockingQueue` |
| Sorted concurrent map | `ConcurrentSkipListMap` |

The key with `ConcurrentHashMap` is to use its **atomic compound operations**, not separate calls:

```java
ConcurrentHashMap<String, LongAdder> perMerchant = new ConcurrentHashMap<>();

// Racy: two threads can both see "absent" and both put
if (!map.containsKey(k)) map.put(k, v);

// Atomic alternatives
map.putIfAbsent(k, v);
map.computeIfAbsent(merchantId, id -> new LongAdder()).increment();
map.merge(account, amount, BigDecimal::add);                      // atomic add-or-insert
map.compute(account, (k, old) -> old == null ? amount : old.add(amount));
```

Keep the functions passed to `compute`/`merge` short and free of I/O: they run while part of the map is locked.

A **blocking queue** is the cleanest way to coordinate threads, because the queue does the synchronisation:

```java
BlockingQueue<Transaction> queue = new ArrayBlockingQueue<>(10_000);
// producer (file reader)
queue.put(tx);                  // blocks when full: natural back-pressure
// consumers (posting workers)
Transaction tx = queue.take();  // blocks when empty
```

## 7. Deadlock and how to avoid it

A deadlock happens when threads each hold a lock the other needs:

```java
void transfer(Account from, Account to, BigDecimal amt) {
    synchronized (from) {
        synchronized (to) { ... }
    }
}
// Thread 1: transfer(A, B)  holds A, waits for B
// Thread 2: transfer(B, A)  holds B, waits for A   → both wait forever
```

All four conditions must hold for a deadlock: mutual exclusion, hold-and-wait, no pre-emption and a circular wait. Break any one:

- **Lock ordering** (breaks the cycle): always lock accounts in a global order, for example by account number.

  ```java
  Account first = from.number().compareTo(to.number()) < 0 ? from : to;
  Account second = first == from ? to : from;
  synchronized (first) { synchronized (second) { ... } }
  ```

- **Timeouts** with `tryLock(timeout)`: back off and retry instead of waiting forever.
- **Fewer locks**: one lock, or no shared mutable state at all.

To diagnose a live deadlock, take a thread dump (`jcmd <pid> Thread.print` or `jstack <pid>`). The JVM reports "Found one Java-level deadlock" and lists the threads and locks involved.

Related liveness problems: **livelock** (threads keep reacting to each other and make no progress, like two people stepping aside in a corridor) and **starvation** (a thread never gets the lock because others always win).

## 8. Coordination helpers

- `Semaphore(n)`: allow at most *n* concurrent holders. Perfect for "no more than 10 concurrent calls to the NIBSS sandbox".
- `CountDownLatch(n)`: wait until *n* events have happened, for example until all file-loading threads finish.
- `CyclicBarrier`, `Phaser`: groups of threads that proceed in phases.

```java
private final Semaphore permits = new Semaphore(10);

public Response callPartner(Request r) throws InterruptedException {
    permits.acquire();
    try { return client.send(r); }
    finally { permits.release(); }
}
```

## 9. The best lock is no lock

The most reliable concurrent code avoids shared mutable state:

- **Immutability** (topic 1.7): immutable objects need no locks.
- **Confinement**: give each thread its own data, then merge results at the end (as the statement generator in topic 2.1 did).
- **Message passing**: hand data between threads through a queue instead of sharing it.
- **Let the database do it**: for money, the database transaction and its locks are the source of truth (topic 3.2).

## Worked example: a thread-safe daily limit tracker

```java
public final class DailyLimitTracker {
    private final BigDecimal limit;
    private final ConcurrentHashMap<String, BigDecimal> usedToday = new ConcurrentHashMap<>();

    public DailyLimitTracker(BigDecimal limit) { this.limit = limit; }

    /** Atomically reserves the amount if it fits under the limit. */
    public boolean tryReserve(String account, BigDecimal amount) {
        var accepted = new boolean[1];
        usedToday.compute(account, (k, used) -> {
            BigDecimal current = used == null ? BigDecimal.ZERO : used;
            BigDecimal next = current.add(amount);
            if (next.compareTo(limit) > 0) { accepted[0] = false; return current; }
            accepted[0] = true;
            return next;
        });
        return accepted[0];
    }

    public void release(String account, BigDecimal amount) {
        usedToday.computeIfPresent(account, (k, used) -> used.subtract(amount));
    }

    public void resetForNewDay() { usedToday.clear(); }
}
```

Check and update happen inside one atomic `compute`, so two concurrent ₦400,000 transfers against a ₦500,000 limit can never both succeed. In production, this state would also be persisted (or kept in Redis, topic 3.6) so it survives restarts and is shared across instances.

## Common mistakes

- Synchronising writes but not reads.
- `volatile` used for counters.
- `containsKey` followed by `put` on a `ConcurrentHashMap`.
- Calling I/O or external services while holding a lock.
- `lock()` without `unlock()` in a `finally`.
- Nested locks in inconsistent order.
- Assuming an in-memory lock protects a balance shared by several service instances.

## Practice

1. Reproduce the double-withdraw race with 100 threads and a `CountDownLatch` that releases them at once. Fix it three ways: `synchronized`, `ReentrantLock` and `AtomicReference` with CAS. Benchmark them roughly.
2. Write the deadlocking `transfer` above, trigger it, take a thread dump with `jcmd`, and find the deadlock report. Fix it with lock ordering.
3. Count transactions per merchant from 8 threads using a plain `HashMap`, then `ConcurrentHashMap` with `merge`. Compare totals against the expected count.
4. Wrap a fake partner API in a `Semaphore(5)` and prove with logs that no more than five calls are ever in flight.

## Key terms

Race condition, check-then-act, read-modify-write, critical section, atomicity, visibility, ordering, Java Memory Model, happens-before, monitor, `synchronized`, `ReentrantLock`, `tryLock`, `ReadWriteLock`, `volatile`, CAS, atomic classes, `LongAdder`, `ConcurrentHashMap`, blocking queue, deadlock, livelock, starvation, lock ordering, semaphore, latch.

## Further reading

- [The Java Tutorials: Synchronization](https://docs.oracle.com/javase/tutorial/essential/concurrency/sync.html)
- [The Java Tutorials: High Level Concurrency Objects](https://docs.oracle.com/javase/tutorial/essential/concurrency/highlevel.html)
- [`java.util.concurrent` package summary (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html)
- [JSR-133 (Java Memory Model) FAQ](https://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html)
