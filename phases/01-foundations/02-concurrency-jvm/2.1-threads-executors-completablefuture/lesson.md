## What you will be able to do

- Explain processes, threads, concurrency and parallelism, and why backend servers are concurrent by nature.
- Create threads and understand their lifecycle, `join`, interruption and daemon threads.
- Run work on thread pools with `ExecutorService`, `Callable` and `Future`, and shut pools down correctly.
- Compose asynchronous work with `CompletableFuture`: chaining, combining, timeouts and error handling.
- Pick sensible pool sizes for CPU-bound and I/O-bound work.

## Why this matters in a bank or fintech

A payment API serves thousands of customers at once; a statement batch processes millions of rows overnight; a balance enquiry may call the core banking system, a fraud score and a loyalty service at the same time. All of that is concurrency. Get it right and the end-of-day batch finishes in 20 minutes instead of three hours. Get it wrong and you get the bugs in topic 2.2: double debits, lost updates and frozen services. Concurrency questions are standard in interviews for Lagos fintechs and banks alike.

## 1. Concepts

- A **process** is a running program with its own memory. Your Spring Boot application is one JVM process.
- A **thread** is an independent path of execution *inside* a process. Threads share the process's heap memory, which is what makes them powerful and dangerous.
- **Concurrency** means dealing with many things at once (interleaving them); **parallelism** means doing many things at the same instant on multiple CPU cores. A web server handling 500 requests on 8 cores is concurrent, and partly parallel.
- Work is **CPU-bound** (calculating interest for a million accounts) or **I/O-bound** (waiting on a database, an HTTP call or a file). Most backend work is I/O-bound: threads spend most of their time waiting.

## 2. Threads

```java
Runnable task = () -> System.out.println("Running on " + Thread.currentThread().getName());

Thread t = new Thread(task, "statement-worker-1");
t.start();          // start() creates a new thread; run() would just call the method on the current one
t.join();           // wait for it to finish
```

Java 21 also offers builders: `Thread.ofPlatform().name("worker-", 0).start(task)` and, for virtual threads, `Thread.ofVirtual().start(task)` (topic 2.3).

A thread moves through states: `NEW` → `RUNNABLE` → (`BLOCKED` / `WAITING` / `TIMED_WAITING`) → `TERMINATED`. You will see these names in thread dumps (`jstack <pid>` or `jcmd <pid> Thread.print`) when diagnosing a stuck service.

### Interruption: how to ask a thread to stop

Java has no safe way to kill a thread. Instead you **interrupt** it, and well-written code checks for that and exits:

```java
Thread poller = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            pollSettlementFile();
            Thread.sleep(Duration.ofSeconds(30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // restore the flag, then exit the loop
            return;
        }
    }
});
poller.start();
// later, on shutdown:
poller.interrupt();
```

**Never swallow `InterruptedException`.** Either rethrow it or restore the interrupt flag, otherwise shutdowns hang.

**Daemon threads** (`t.setDaemon(true)`) do not keep the JVM alive. Use them only for background helpers whose work can be abandoned.

Creating raw threads by hand is rare in application code. Threads are expensive to create (a platform thread reserves around 1 MB of stack and an OS thread), and unbounded thread creation will exhaust memory. You use pools.

## 3. ExecutorService: thread pools

An `ExecutorService` owns a pool of threads and a queue of tasks. You submit work; it schedules it.

```java
ExecutorService pool = Executors.newFixedThreadPool(8);

Future<BigDecimal> f = pool.submit(() -> interestFor(account));   // Callable returns a value
BigDecimal interest = f.get(5, TimeUnit.SECONDS);                  // blocks until done or times out

pool.shutdown();                                     // stop accepting tasks; finish queued ones
if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
    pool.shutdownNow();                              // interrupt running tasks
}
```

- `Runnable` returns nothing; `Callable<V>` returns a value and may throw checked exceptions.
- `Future.get()` blocks. If the task threw, `get()` throws `ExecutionException` wrapping the cause.
- `invokeAll(tasks)` submits a batch and waits for all; `invokeAny` returns the first success.
- Since Java 19, `ExecutorService` is `AutoCloseable`: `try (var pool = Executors.newFixedThreadPool(8)) { ... }` waits for submitted tasks when the block ends.

### Factory methods and their traps

| Factory | Behaviour | Trap |
| --- | --- | --- |
| `newFixedThreadPool(n)` | n threads, **unbounded** queue | Queue grows forever under overload → OutOfMemoryError |
| `newCachedThreadPool()` | creates threads on demand, reuses idle ones | **Unbounded threads** under load |
| `newSingleThreadExecutor()` | one thread, tasks run in order | Unbounded queue |
| `newScheduledThreadPool(n)` | delayed and periodic tasks | Exceptions silently cancel a periodic task |
| `newVirtualThreadPerTaskExecutor()` | one virtual thread per task (Java 21) | See topic 2.3 |

For production pools, construct a `ThreadPoolExecutor` directly with a **bounded queue** and an explicit **rejection policy**, and give threads meaningful names so thread dumps make sense:

```java
ExecutorService pool = new ThreadPoolExecutor(
        8, 16,                                       // core and maximum threads
        60, TimeUnit.SECONDS,                        // idle time before extra threads die
        new ArrayBlockingQueue<>(1_000),             // bounded queue: back-pressure
        Thread.ofPlatform().name("recon-", 0).factory(),
        new ThreadPoolExecutor.CallerRunsPolicy());  // when full, the caller runs the task (slows producers)
```

In Spring (Module 4) you will usually configure pools through properties rather than by hand, but the same ideas apply.

### How big should a pool be?

- **CPU-bound** work: about the number of cores (`Runtime.getRuntime().availableProcessors()`). More threads just fight over the same cores.
- **I/O-bound** work: more threads, roughly `cores × (1 + wait time / compute time)`. A task that waits 90 ms on the database for every 10 ms of CPU could use about `8 × 10 = 80` threads on 8 cores.
- Measure, then tune. And note the downstream limit: 200 threads hitting a database pool of 20 connections just queue at the database. Virtual threads (topic 2.3) change this arithmetic for I/O-bound work.

## 4. CompletableFuture: composing asynchronous work

`Future` can only be waited on. `CompletableFuture` can be **chained and combined** without blocking, which is how you express "call three services in parallel, then combine the results".

```java
ExecutorService io = Executors.newFixedThreadPool(32);

CompletableFuture<Account> account = CompletableFuture.supplyAsync(() -> core.account(number), io);
CompletableFuture<Integer> fraudScore = CompletableFuture.supplyAsync(() -> fraud.score(number), io);
CompletableFuture<Integer> points = CompletableFuture.supplyAsync(() -> loyalty.points(number), io);

CompletableFuture<Dashboard> dashboard = account
        .thenCombine(fraudScore, (acc, score) -> new Partial(acc, score))
        .thenCombine(points, (partial, pts) -> new Dashboard(partial.acc(), partial.score(), pts))
        .orTimeout(2, TimeUnit.SECONDS)
        .exceptionally(ex -> Dashboard.degraded(number, ex));

Dashboard d = dashboard.join();   // total latency ≈ the slowest call, not the sum
```

The key methods, grouped by what they do:

| Purpose | Methods |
| --- | --- |
| Start | `supplyAsync(supplier, executor)`, `runAsync(runnable, executor)`, `completedFuture(value)` |
| Transform the result | `thenApply(fn)` (like `map`) |
| Chain another async step | `thenCompose(fn)` (like `flatMap`: fn returns a CompletableFuture) |
| Combine two independent futures | `thenCombine(other, fn)` |
| Consume | `thenAccept(consumer)`, `thenRun(runnable)` |
| Wait for many | `allOf(f1, f2, f3)`, `anyOf(...)` |
| Errors | `exceptionally(fn)`, `handle((value, ex) -> ...)`, `whenComplete(...)` |
| Time limits | `orTimeout(d, unit)`, `completeOnTimeout(fallback, d, unit)` |
| Get the result | `join()` (unchecked exception) or `get()` (checked) |

`thenApply` versus `thenCompose` is the classic interview question. If the next step returns a plain value, use `thenApply`. If it returns another `CompletableFuture` (another async call), use `thenCompose`, or you will end up with `CompletableFuture<CompletableFuture<T>>`.

```java
CompletableFuture<Receipt> receipt = validate(request)          // CF<ValidatedRequest>
        .thenCompose(valid -> debit(valid))                     // debit returns CF<DebitResult>
        .thenCompose(debit -> credit(debit))                    // credit returns CF<Receipt>
        .thenApply(r -> r.withChannel("MOBILE"));               // plain transformation
```

**Always pass an executor.** Without one, `supplyAsync` runs on the shared `ForkJoinPool.commonPool()`, which is sized for CPU work. Blocking I/O there starves everything else in the JVM that uses it, including parallel streams.

**Handle failures explicitly.** An exception inside a stage completes the future exceptionally; later `thenApply` stages are skipped; `join()` throws `CompletionException` wrapping the cause. If nobody calls `join` or attaches `exceptionally`/`handle`, the error vanishes silently.

## Worked example: a parallel statement generator

A bank needs PDF statements for 50,000 accounts. Each takes about 100 ms, mostly waiting on the database.

```java
List<String> accounts = repo.accountsForStatementRun(runDate);

try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
    List<CompletableFuture<StatementResult>> futures = accounts.stream()
            .map(acc -> CompletableFuture
                    .supplyAsync(() -> generator.generate(acc, runDate), pool)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .handle((ok, ex) -> ex == null
                            ? StatementResult.ok(acc, ok)
                            : StatementResult.failed(acc, ex.getMessage())))
            .toList();

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    Map<Boolean, Long> outcome = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.partitioningBy(StatementResult::isOk, Collectors.counting()));
    log.info("Statements: {} ok, {} failed", outcome.get(true), outcome.get(false));
}
```

Sequentially: 50,000 × 100 ms ≈ 83 minutes. With 32 threads: about 3 minutes, as long as the database can serve 32 concurrent queries. Failures are collected per account instead of aborting the run, so operations can re-run just the failures.

## Common mistakes

- Calling `run()` instead of `start()`.
- Swallowing `InterruptedException`.
- Unbounded pools or queues (`newCachedThreadPool`, `newFixedThreadPool` under overload).
- Forgetting to shut down executors, so the JVM never exits or threads leak on redeploy.
- Blocking I/O on the common ForkJoinPool via `supplyAsync` with no executor.
- `thenApply` where `thenCompose` is needed.
- `CompletableFuture` chains with no error handling.

## Practice

1. Write a program that computes simple interest for 1,000,000 synthetic accounts sequentially and with a fixed pool of `availableProcessors()` threads. Compare the timings. Then add `Thread.sleep(1)` to each task to simulate I/O and compare a pool of 8 with a pool of 64.
2. Build the dashboard example with three fake services that sleep 300 ms, 500 ms and 800 ms. Show the total time is about 800 ms, not 1.6 s. Make the fraud service throw and return a degraded dashboard instead.
3. Write a `ScheduledExecutorService` job that "polls" a settlement folder every 10 seconds, shuts down cleanly on Ctrl+C (add a shutdown hook), and survives an exception in one poll.
4. Configure a `ThreadPoolExecutor` with a queue of 10 and `AbortPolicy`, submit 100 slow tasks, and catch the `RejectedExecutionException`. Then switch to `CallerRunsPolicy` and explain the difference.

## Key terms

Process, thread, concurrency, parallelism, CPU-bound, I/O-bound, thread states, interrupt, daemon thread, `ExecutorService`, thread pool, `Callable`, `Future`, bounded queue, rejection policy, `CompletableFuture`, `thenApply`, `thenCompose`, `thenCombine`, `allOf`, common ForkJoinPool.

## Further reading

- [The Java Tutorials: Concurrency](https://docs.oracle.com/javase/tutorial/essential/concurrency/index.html)
- [`ExecutorService` API (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [`CompletableFuture` API (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- Brian Goetz et al., *Java Concurrency in Practice*, chapters 6–8 (book; still the reference)
- [Oracle: Java SE 21 Core Libraries, Concurrency](https://docs.oracle.com/en/java/javase/21/core/concurrency.html)
