## What you will be able to do

- Record a running JVM with Java Flight Recorder (JFR) and analyse it with `jfr view` and JDK Mission Control.
- Get JDK tools into a slim production container safely, with a debug container.
- Find CPU hot spots, lock contention and allocation pressure, and read a flame graph from async-profiler.
- Diagnose memory problems with GC logs, class histograms and heap dumps in Eclipse MAT.

## Why this matters in a bank or fintech

Metrics and traces tell you **where** time goes between services. Inside one JVM, they stop: a span that says "AccountController.hold took 600 ms" doesn't say whether that was CPU, a lock, garbage collection or waiting on I/O. Profilers answer that, and they work in production. In ITL Pay's load test (topic 13.5), a circuit breaker opened under load; JFR showed in two minutes that the cause was one lock in accounts. It also found a lock inside two libraries that nobody would have guessed. Guessing is expensive when payments are failing.

## 1. Java Flight Recorder

**JFR** is built into the JDK. It records events (method samples, locks, GC, allocations, I/O, exceptions, thread states) into a compact file with low overhead: about 1% with the `default` settings and a little more with `profile`, which is why it's safe to use in production.

```bash
jcmd <pid> JFR.start name=load settings=profile filename=/tmp/transfers.jfr
# ... reproduce the problem ...
jcmd <pid> JFR.stop name=load            # writes the file
```

You can also record from start-up with `-XX:StartFlightRecording=duration=5m,filename=/tmp/start.jfr`, or keep a rolling recording running permanently (`maxage=30m`) and dump it when something goes wrong: a flight recorder in the true sense.

## 2. Tools in a slim container

ITL Pay's images use the JRE, which has `java` and `jfr` but not `jcmd`. Don't add a JDK to production images; bring the tools in a **debug container** that shares the app's process namespace, the same idea as `kubectl debug --target` in Kubernetes:

```bash
G=$(docker exec itl-pay-starter-transfers-1 id -g)                 # the app's group (999 here)
docker run --rm --pid=container:itl-pay-starter-transfers-1 \
    --user 10001:$G eclipse-temurin:21-jdk jcmd 1 VM.version
```

The JVM's attach mechanism only accepts a client with the **same user and group** as the JVM. Our first attempt ran as user 10001 but group 0 and failed with `AttachNotSupportedException ... doesn't respond`; `ls /proc/1/root/tmp` from the debug container said "Permission denied", which pointed at the mismatch. With `--user 10001:999` it attached at once. On Kubernetes, give the debug container the same `runAsUser` and `runAsGroup` as the app.

## 3. A real recording: what JFR found under load

We started a `profile` recording in accounts and transfers, ran the k6 load test (topic 13.5), stopped it, copied the files out (`docker cp`) and used the JDK's `jfr view` command, which prints ready-made reports.

**Accounts, lock contention:**

```
$ jfr view contention-by-site accounts.jfr
StackTrace                                                                   Count    Avg.    Max.
AccountController.hold(String, AccountController$HoldRequest)                  846 94.8 ms  678 ms
```

`hold` is `synchronized`: every hold for every account waits for one lock. At 60 transfers a second, threads waited 95 ms on average and up to 678 ms just to enter it. That's what pushed some calls past transfers' 2-second timeout and opened the circuit breaker. The fix isn't a bigger timeout: it's no global lock. The `v0.1` design already removes it: holds in PostgreSQL with a conditional `UPDATE ... WHERE available >= :amount` and `UNIQUE (account_id, reference)` (topic 10.4) lock only the row of the account involved.

**Transfers, a surprise inside libraries:**

```
$ jfr view contention-by-site transfers.jfr
StackTrace                                                    Count    Avg.     Max.
java.util.Hashtable.get(Object)                                 161  417 ms   1.86 s
org.apache.tomcat.util.collections.SynchronizedStack.pop()       15  459 ms   1.05 s
```

`jfr print --events jdk.JavaMonitorEnter --stack-depth 25` showed the full path:

```
org.apache.commons.logging.impl.WeakHashtable.get(Object)
org.apache.commons.logging.LogFactory.getCachedFactory(ClassLoader)
org.apache.commons.logging.LogFactory.getLog(Class)
org.springframework.data.redis.connection.AbstractRedisConnection.<init>()
org.springframework.data.redis.connection.lettuce.LettuceConnection.<init>(...)
org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.getConnection()
org.springframework.data.redis.cache.DefaultRedisCacheWriter.execute(String, Function)
```

Every Redis cache read creates a small connection wrapper, its constructor looks up a logger, and the logging library's lookup goes through a synchronized table. Under load, threads queued there for up to 1.86 seconds. Nobody would guess this from reading ITL Pay's code. What to do with a finding like this: confirm it (run the same load with the cache off and compare), check the libraries' issue trackers and newer versions, and decide whether the cache earns its cost (topic 13.4). Don't patch around it blindly.

**Transfers, GC was healthy:** `jfr view gc-pauses` showed 43 pauses totalling 1.75 s over the run, median 30 ms. Not the problem, and it's as useful to rule something out quickly as to find it.

Other views worth knowing: `hot-methods` (where CPU goes), `allocation-by-class`, `exception-count`, `thread-cpu-load`, `jvm-information`. For a visual tool, open the `.jfr` file in **JDK Mission Control**.

## 4. Flame graphs with async-profiler

**async-profiler** samples stacks (CPU, allocations, locks, wall clock) and draws a **flame graph**: each box is a method, its width is the share of samples, callers below callees. You look for wide plateaus. It's the usual tool when you want a picture of "where does the CPU go" in one glance:

```bash
asprof -d 30 -e cpu -f /tmp/cpu.html <pid>
asprof -d 30 -e lock -f /tmp/locks.html <pid>
```

In containers, CPU profiling with perf events may need extra privileges; `-e itimer` works without them. JFR can produce similar views (`jfr view hot-methods`, or flame graphs in Mission Control), so start with JFR, which is already in your JVM.

## 5. Memory: GC logs, histograms and heap dumps

Symptoms of a memory problem: heap after each full GC keeps rising, GC takes more and more time, then `OutOfMemoryError` or `OOMKilled` (topic 12.2 explains the difference).

1. **GC logs** (`-Xlog:gc*:file=/tmp/gc.log`) or the `jvm_gc_pause_seconds` and `jvm_memory_used_bytes` metrics show the trend.
2. A **class histogram** shows what fills the heap now, cheaply:
   ```
   $ jcmd 1 GC.class_histogram
    num     #instances         #bytes  class name
      1:        165249       21575896  [B (java.base)
      2:          1132        5117136  [C (java.base)
      3:        151524        3636576  java.lang.String (java.base)
   ```
   Take two, a few minutes apart under load; a class whose count only grows is your suspect. Here, byte arrays and strings are normal for a web service.
3. A **heap dump** captures every object: `jcmd 1 GC.heap_dump /tmp/heap.hprof` (this pauses the JVM, and the file is as big as the heap and contains customer data, so treat it like production data). Set `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps` so you get one automatically.
4. Open it in **Eclipse Memory Analyzer (MAT)**: the "Leak Suspects" report, then the **dominator tree** (which objects keep the most memory alive) and the path to GC roots (why they can't be collected).

The classic leaks in Spring services: a `static Map` used as a cache with no limit, `ThreadLocal`s not cleared on pooled threads, listeners registered and never removed, and unbounded in-memory queues. Also check non-heap: `GC.heap_info` on transfers showed 62 MB of heap in use but 108 MB of **metaspace**, which is why a container's memory limit must leave room beyond the heap.

## Worked example: the breaker that opened at 60 transfers a second

1. The k6 spike test produced 215 `503` responses. The dashboard showed the `accounts` circuit breaker opening; Prometheus showed 14 failed calls (timeouts) followed by 212 calls rejected fast.
2. `http_server_requests_seconds_max` for accounts' `/accounts/{id}/holds` had reached 3.1 s, though the endpoint does almost nothing. GC pauses in accounts stayed under 110 ms, so not GC.
3. A JFR recording during the next run: `contention-by-site` named `AccountController.hold` (846 contended entries, up to 678 ms).
4. Root cause: one `synchronized` method for all accounts. Fix: per-account locking in the database (the `v0.1` task), plus a better breaker window (topic 13.5).

## Common mistakes

- Optimising without measuring, or measuring on a laptop and assuming production behaves the same.
- Shipping a full JDK in production images to get `jcmd`, instead of a debug container.
- Running a debug container with the wrong user or group and concluding attach "doesn't work".
- Taking heap dumps casually: they pause the JVM and contain customer data.
- Treating every allocation as a problem; the JVM is built for short-lived objects.
- Ignoring locks: CPU can be idle while every thread waits on one monitor.

## Practice

1. Repeat the recording in this lesson: start JFR in accounts with a debug container, run the k6 test, and confirm the contention on `hold`. Then make `hold` lock per account (a `ConcurrentHashMap.compute` per account id) and measure again.
2. Run the load test with the Redis cache turned off for transfers (`spring.cache.type=none` won't override a custom cache manager; add a property to choose) and compare latency and the `Hashtable` contention.
3. Add a deliberate leak to fraud (a static list of every event), run load for 5 minutes, take two class histograms and a heap dump, and find it in MAT's dominator tree.
4. Record a CPU flame graph of the gateway under load with async-profiler (`-e itimer`) and name the widest frame that isn't Tomcat or the JVM.
5. Write the runbook section "how to profile a production pod", including the security rules for heap dumps.

## Key terms

Profiling, Java Flight Recorder, JFR event, recording settings (`default`, `profile`), `jcmd`, `jfr view`, JDK Mission Control, attach mechanism, debug container, `kubectl debug`, hot method, lock contention, monitor, allocation pressure, async-profiler, flame graph, GC log, GC pause, class histogram, heap dump, HPROF, Eclipse MAT, dominator tree, GC roots, memory leak, metaspace.

## Further reading

- [Oracle: JDK Flight Recorder and the jfr tool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)
- [Oracle: jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [async-profiler](https://github.com/async-profiler/async-profiler)
- [Eclipse Memory Analyzer](https://eclipse.dev/mat/)
- [Kubernetes: debugging running pods with ephemeral containers](https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/#ephemeral-container)
