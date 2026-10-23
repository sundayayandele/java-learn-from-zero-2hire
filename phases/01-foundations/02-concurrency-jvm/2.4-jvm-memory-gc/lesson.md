## What you will be able to do

- Describe what happens from `java -jar app.jar` to running code: class loading, bytecode interpretation and JIT compilation.
- Explain the JVM's memory areas: heap, thread stacks, metaspace and native memory, and what lives where.
- Explain how garbage collection works (reachability, generations) and compare G1, ZGC, Parallel and Serial collectors.
- Size the heap for a container, read GC logs at a basic level, and diagnose `OutOfMemoryError` and `StackOverflowError`.
- Use the basic JDK diagnostic tools: `jcmd`, `jstat`, heap dumps and Java Flight Recorder.

## Why this matters in a bank or fintech

Payment services have latency targets: a card authorisation must answer within a couple of seconds end to end, and your service gets a small slice of that. A 2-second garbage-collection pause, or a container killed for exceeding its memory limit during the month-end batch, turns directly into declined transactions and incident reports. Senior Lagos fintech postings list "JVM tuning" and "heap analysis" explicitly; juniors who can read a GC log and explain heap versus stack stand out.

## 1. How Java runs your code

1. **Class loading.** The JVM loads `.class` files on demand through class loaders (bootstrap → platform → application), then **verifies** the bytecode is safe and **links** it.
2. **Interpretation.** Methods first run in the bytecode interpreter, which starts quickly but runs slowly.
3. **JIT compilation.** The JVM profiles which methods are "hot" and compiles them to optimised machine code. HotSpot uses tiered compilation: the quick C1 compiler first, then the aggressive C2 compiler for the hottest code, with optimisations such as inlining and escape analysis.

That is why a freshly started service is slower for its first minute or so (**warm-up**), and why performance tests must warm up before measuring. Projects such as CDS/AOT caches (improved in Java 24–25) and GraalVM native images reduce startup time; you will meet them later.

## 2. Memory areas

```text
JVM process memory
├── Heap (shared by all threads)                    -Xmx / -XX:MaxRAMPercentage
│    ├── Young generation: Eden + Survivor spaces   new objects
│    └── Old generation                              long-lived objects
├── Thread stacks (one per platform thread)         -Xss, ~1 MB default
├── Metaspace (native)                              class metadata
├── Code cache (native)                             JIT-compiled machine code
└── Other native memory                              direct buffers, GC structures, threads, JNI
```

### Stack

Each thread has its own **stack** of **frames**: one frame per method call, holding that call's local variables, parameters and return address. A frame is pushed when a method is called and popped when it returns. Stack memory is fast, automatically managed and private to the thread.

Local variables of primitive types live in the frame. For objects, the **reference** lives in the frame and the **object** lives on the heap:

```java
void post(Transaction tx) {
    long amountKobo = tx.amountKobo();           // primitive: in post()'s stack frame
    BigDecimal fee = calculateFee(amountKobo);   // reference in the frame, BigDecimal object on the heap
}
```

Infinite or very deep recursion overflows the stack: `StackOverflowError`. (Virtual threads keep their stacks on the heap when unmounted, which is why they are so cheap.)

### Heap

All objects and arrays live on the **heap**, shared by all threads. That sharing is why topic 2.2 matters. You never free heap memory yourself; the **garbage collector** reclaims objects that are no longer reachable.

### Metaspace and native memory

Class metadata lives in **metaspace**, outside the heap. It grows with the number of loaded classes; leaks here usually come from class loaders that are never released (for example, repeated hot redeploys). Thread stacks, the JIT code cache, direct `ByteBuffer`s (used by Netty and many drivers) and GC bookkeeping are also native memory. This matters in containers: **total process memory is always more than the heap**.

## 3. Garbage collection

### Reachability

The GC starts from **GC roots** (local variables on thread stacks, static fields, JNI references, active threads) and follows references. Anything reachable is live; everything else is garbage. Cycles are not a problem: two objects pointing at each other but unreachable from any root are both collected.

### The generational hypothesis

Most objects die young: the `BigDecimal` intermediate in a fee calculation, the DTO for one HTTP request, the strings in a log message. A few live long: caches, connection pools, Spring beans. Collectors exploit this:

- New objects are allocated in **Eden** (very fast: a pointer bump).
- A **minor (young) collection** copies the few survivors from Eden into a **survivor** space and discards the rest wholesale. Cheap, because it only touches live objects.
- Objects that survive several minor collections are **promoted** to the **old generation**.
- The old generation is collected less often, with more expensive **major/mixed** collections.

### Stop-the-world pauses

Some GC phases pause all application threads ("stop-the-world"). Modern collectors do most of their work **concurrently** with the application, shrinking pauses, at the cost of some CPU and memory overhead. The trade-off is always between **throughput** (total work done), **latency** (pause times) and **footprint** (memory used).

### The collectors

| Collector | Flag | Pauses | Best for |
| --- | --- | --- | --- |
| **G1** (default) | `-XX:+UseG1GC` | Targets a pause goal (default 200 ms), typically tens of ms | General-purpose services: the right default |
| **ZGC** (generational since 21, default mode since 23) | `-XX:+UseZGC` | Sub-millisecond, independent of heap size | Latency-critical APIs, large heaps |
| **Parallel** | `-XX:+UseParallelGC` | Longer pauses, highest throughput | Batch jobs where pauses don't matter |
| **Serial** | `-XX:+UseSerialGC` | Single-threaded | Tiny containers, < 2 CPUs or very small heaps |
| Shenandoah | `-XX:+UseShenandoahGC` | Low pause | Alternative to ZGC in some distributions |

G1 divides the heap into equal regions and collects the regions with the most garbage first ("garbage first"). ZGC uses coloured pointers and load barriers to move objects while the application runs. For a card-authorisation service with tight p99 latency, test ZGC; for an overnight reconciliation batch, Parallel may finish fastest. Measure your workload before switching: the default is usually right.

Note that the JVM may pick Serial GC automatically if it thinks it is on a small machine (fewer than 2 CPUs or under about 1.8 GB memory), which surprises people running small containers.

## 4. Sizing memory, especially in containers

```bash
java -Xms512m -Xmx2g -jar app.jar             # fixed initial and maximum heap
java -XX:MaxRAMPercentage=75 -jar app.jar     # heap as a share of the container's memory limit
```

Since Java 10 the JVM is **container-aware**: it reads the cgroup memory and CPU limits. By default the maximum heap is only **25%** of the available memory, which wastes most of a container. For a dedicated service container, `-XX:MaxRAMPercentage=70` to `75` is common, leaving the rest for metaspace, thread stacks, code cache and direct buffers.

If total process memory exceeds the container limit, the kernel's OOM killer terminates the process (Kubernetes shows `OOMKilled`, exit code 137), **with no Java error at all**. If the heap fills, you get a Java `OutOfMemoryError: Java heap space`. Different symptoms, different fixes.

Rules of thumb for a typical Spring Boot service:

- Start with a container of 1–2 GB and `MaxRAMPercentage=75`, then measure.
- Keep `-Xms` equal to `-Xmx` (or set `InitialRAMPercentage` to match) in production to avoid resize pauses.
- Watch the **heap after GC** over time, not the peak: a healthy service shows a saw-tooth that returns to a stable floor.

## 5. When memory goes wrong

| Symptom | Likely cause | First steps |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Leak, or genuinely too much live data | Heap dump, look at the biggest retainers |
| `OutOfMemoryError: Metaspace` | Class-loader leak, too many generated classes | Check redeploys, proxies, scripting engines |
| `OutOfMemoryError: unable to create native thread` | Too many platform threads | Thread dump; bound your pools, consider virtual threads |
| Container `OOMKilled` (137) | Total process memory > limit | Lower `MaxRAMPercentage`, check native memory |
| `StackOverflowError` | Runaway recursion | Read the repeating frames in the stack trace |
| Rising latency, high CPU, frequent full GCs | Heap too small, or a leak approaching the limit | GC logs: is heap after GC climbing? |

### Memory leaks in Java

The GC can only free unreachable objects. A Java "leak" is objects that stay reachable by accident:

- static collections that only grow (`static Map<String, Session> CACHE` with no eviction);
- caches without size limits or expiry (use Caffeine or Redis with TTLs, topic 3.6);
- listeners registered and never removed;
- `ThreadLocal` values in pooled threads never cleared;
- unclosed resources holding buffers.

## 6. Tools you should be able to use

```bash
jcmd                                   # list running JVMs and their process IDs
jcmd <pid> VM.flags                    # the effective JVM flags (which GC? which heap size?)
jcmd <pid> GC.heap_info                # current heap usage
jcmd <pid> Thread.print                # thread dump (deadlocks, stuck threads)
jcmd <pid> GC.heap_dump /tmp/heap.hprof    # heap dump for leak analysis
jstat -gcutil <pid> 1000               # GC statistics every second
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar app.jar   # unified GC logging
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -jar app.jar   # automatic dump on OOM
jcmd <pid> JFR.start duration=60s filename=rec.jfr   # Java Flight Recorder: low-overhead profiling
```

Open heap dumps in **Eclipse Memory Analyzer (MAT)** or IntelliJ's profiler and look at the "dominator tree": which objects retain the most memory. Open JFR recordings in **JDK Mission Control**. Module 13 goes deeper; in Phase 1 you need to know these exist and run them once.

A GC log line from G1 looks like:

```text
[12.345s][info][gc] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 812M->204M(1024M) 18.3ms
```

Read it as: the 42nd collection, a young pause, heap went from 812 MB used to 204 MB used out of 1024 MB committed, and the application was paused for 18.3 ms. If the number after the arrow keeps rising across collections, live data is growing.

## Worked example: an unbounded cache

```java
@Service
public class FxRateService {
    private static final Map<String, BigDecimal> CACHE = new HashMap<>();   // grows forever

    public BigDecimal rate(String base, String quote, LocalDate date) {
        return CACHE.computeIfAbsent(base + quote + date, k -> provider.fetch(base, quote, date));
    }
}
```

Every distinct date adds an entry and nothing ever leaves. After months in production, `heap after GC` creeps upward, full GCs become frequent, latency degrades, and eventually `OutOfMemoryError`. A heap dump shows `HashMap$Node[]` dominating, held by `FxRateService.CACHE`. Fixes: a bounded cache with expiry (Caffeine's `maximumSize` and `expireAfterWrite`), or Redis with a TTL. And `HashMap` here is also not thread-safe (topic 2.2).

## Common mistakes

- Setting `-Xmx` equal to the container limit, leaving no room for native memory.
- Assuming the default heap uses most of the container (it uses 25%).
- Switching collectors without measuring.
- Calling `System.gc()` in application code.
- Unbounded static caches and collections.
- Benchmarking without warm-up.

## Practice

1. Run any Spring Boot or plain Java app, find it with `jcmd`, and print its flags. Which GC is it using, and what is the max heap? Run it again in Docker with `--memory=512m` and compare.
2. Write a program that adds 1 KB byte arrays to a static list forever, run it with `-Xmx128m -XX:+HeapDumpOnOutOfMemoryError`, and open the dump in Eclipse MAT. Find the leak in the dominator tree.
3. Run the same allocation-heavy program with G1, Parallel and ZGC and `-Xlog:gc`. Compare total runtime and the longest pause.
4. Write a recursive function without a base case, read the `StackOverflowError` trace, then fix it with a loop.

## Key terms

Class loader, bytecode verification, interpreter, JIT, C1/C2, warm-up, stack, frame, heap, metaspace, code cache, native memory, GC roots, reachability, generational hypothesis, Eden, survivor, old generation, minor/major collection, stop-the-world, G1, ZGC, Parallel, Serial, `MaxRAMPercentage`, container awareness, `OOMKilled`, memory leak, heap dump, JFR, `jcmd`.

## Further reading

- [Oracle: Java Garbage Collection Tuning Guide (Java 21)](https://docs.oracle.com/en/java/javase/21/gctuning/introduction-garbage-collection-tuning.html)
- [Oracle: Troubleshooting Guide, memory leaks (Java 21)](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html)
- [`jcmd` reference (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [JEP 439: Generational ZGC](https://openjdk.org/jeps/439)
- [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/)
- [Oracle: The java command (heap and GC options)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)
