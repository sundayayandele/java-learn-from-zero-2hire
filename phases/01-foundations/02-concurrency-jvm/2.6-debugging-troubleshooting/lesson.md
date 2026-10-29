## What you will be able to do

- Read a Java stack trace, including `Caused by` and `Suppressed` sections, and find the line that matters.
- Use the IntelliJ IDEA debugger well: breakpoints, conditional and exception breakpoints, stepping, watches, evaluating expressions, and dropping frames.
- Write logs that make production problems solvable: levels, parameters, structured fields and a **correlation id** carried through every log line of a request.
- Take and read a **thread dump** to diagnose a hung service, and a **heap dump** to find a memory leak.
- Attach a debugger to an application running in Docker, and follow a repeatable troubleshooting method instead of guessing.

## Why this matters in a bank or fintech

Most of a junior engineer's first year is spent reading code and fixing defects, not writing new features. When a customer says "my transfer is stuck", someone has to find out in minutes whether the problem is a bug, a slow database, a hung thread pool or a provider outage. Engineers who can read a stack trace calmly, search logs by a correlation id and take a thread dump are trusted with production support quickly. Interviewers test this directly: "here is a stack trace, what happened?" and "the service stops responding every afternoon; how would you investigate?" are common questions, and some companies run a live "fix this failing service" round.

## 1. A method before tools

Follow the same loop every time:

1. **Reproduce** the problem, ideally as a failing test. If you can't reproduce it, collect evidence first (logs, metrics, request ids, timestamps).
2. **Observe** facts: the exact error, when it started, what changed (deploy, data, traffic, a provider).
3. **Form one hypothesis** and a cheap way to prove it wrong.
4. **Experiment**: debugger, an extra log line, a query, a thread dump.
5. **Fix and protect**: fix the cause, not the symptom, and keep the failing test so it can't come back.

Write down what you tried. "I don't know yet, here is what I've ruled out" is a strong answer in an incident and in an interview.

## 2. Reading stack traces

```text
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "journal_reference_key"]
    at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
    ...
    at ng.itlearning.ledger.journal.LedgerService.transfer(LedgerService.java:87)
    at ng.itlearning.ledger.web.PaymentController.transfer(PaymentController.java:41)
    ...
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement
    ...
Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "journal_reference_key"
  Detail: Key (reference)=(TRF-2026-0001) already exists.
```

How to read it:

- The **first line** is the exception type and message at the top of the chain. The **last `Caused by`** is usually the root cause (here PostgreSQL's unique constraint and the exact key).
- In each block, the **first frame from your own package** (`ng.itlearning…`) is where your code was involved: `LedgerService.java:87`. Framework frames above it show how the error travelled.
- `... 42 more` means "the remaining frames are the same as in the block above".
- `Suppressed:` entries appear when a `try`-with-resources `close()` also failed; they explain secondary failures.

Common exceptions and their usual causes: `NullPointerException` (Java 14+ "helpful" messages name the null variable), `ClassCastException`, `ConcurrentModificationException` (modifying a collection while iterating), `LazyInitializationException` (accessing a lazy JPA association after the session closed, topic 4.4), `BeanCreationException`/`UnsatisfiedDependencyException` at start-up (read down to the root cause: usually a missing property or bean), `SocketTimeoutException` (a dependency is slow).

Never swallow exceptions (`catch (Exception e) {}`) and never log and rethrow the same exception at every layer: you get the same stack trace five times. Log once, where you handle it.

## 3. The IntelliJ IDEA debugger

Start the application or a test with **Debug** instead of Run.

| Tool | Use it when |
| --- | --- |
| Line breakpoint | Stop at a line and inspect variables |
| **Conditional** breakpoint (right-click the breakpoint) | Stop only when `reference.equals("TRF-2026-0001")` or `amount.signum() < 0`, so you don't step through 10,000 loop iterations |
| **Exception** breakpoint (Run → View Breakpoints → +) | Stop where an exception is *thrown*, even if it's caught and hidden somewhere |
| Logpoint (breakpoint with "Suspend" off and "Evaluate and log") | Print a value without editing code or stopping |
| Step Over / Into / Out (F8 / F7 / Shift+F8) | Move through code one line or one call at a time |
| Evaluate Expression (Alt+F8) | Call methods or compute values in the paused context |
| Watches | Keep an expression visible while stepping |
| Drop Frame (Reset Frame) | Re-run the current method from the top after changing a variable |
| Threads view and "Suspend: Thread" | Debug concurrency without freezing every thread |

Debugging tests is often faster than debugging the running app: write a failing test that reproduces the bug, put a breakpoint in it, and debug only that test.

## 4. Logging that solves problems

Use SLF4J with parameters (never string concatenation) and the right level:

```java
private static final Logger log = LoggerFactory.getLogger(TransferService.class);

log.info("Transfer posted reference={} amount={} currency={}", ref, amount, currency);   // business event
log.warn("Provider slow provider={} elapsedMs={}", provider, elapsed);                   // unexpected but handled
log.error("Transfer failed reference={}", ref, e);                                         // needs attention; exception last
log.debug("Fee rule chosen rule={}", rule);                                                // detail, off in production
```

**Correlation ids** connect every log line of one request, across services. Put an id in the SLF4J **MDC** (mapped diagnostic context) at the start of each request, return it to the client, and pass it to downstream calls:

```java
@Component
class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = Optional.ofNullable(req.getHeader("X-Correlation-Id"))
                .filter(v -> v.matches("[A-Za-z0-9-]{8,64}"))          // never trust input blindly
                .orElse(UUID.randomUUID().toString());
        MDC.put("correlationId", id);
        res.setHeader("X-Correlation-Id", id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");                              // threads are reused
        }
    }
}
```

Spring Boot 4 can write **structured JSON logs** with one property, so MDC values become searchable fields in Loki, Elastic or CloudWatch:

```yaml
logging:
  structured:
    format:
      console: ecs        # Elastic Common Schema; logstash and gelf also supported
```

Rules: never log secrets, tokens, full card numbers or BVNs; log identifiers, not whole payloads; and make every `ERROR` actionable.

## 5. Thread dumps: "the service is hung"

When requests time out but CPU is low, threads are usually **waiting**: for a database connection, a lock, a slow HTTP call or each other (deadlock, topic 2.2). A thread dump shows every thread's state and stack at one moment.

```bash
jcmd                                   # list Java processes and their PIDs
jcmd <pid> Thread.print > threads.txt  # or: jstack <pid>
# In Docker:
docker compose exec app jcmd 1 Thread.print > threads.txt
# Via Actuator (if exposed to operators only): GET /actuator/threaddump
```

What to look for:

```text
"http-nio-8080-exec-17" #63 daemon prio=5 os_prio=0 tid=0x... nid=0x5a waiting on condition
   java.lang.Thread.State: TIMED_WAITING (parking)
        at jdk.internal.misc.Unsafe.park(Native Method)
        at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:181)
        at ng.itlearning.ledger.journal.LedgerService.transfer(LedgerService.java:64)
```

- Many request threads parked in `HikariPool.getConnection`: the connection pool is exhausted (long transactions, leaked connections, or a slow query holding connections).
- Many threads `BLOCKED` on the same monitor: lock contention.
- `Found one Java-level deadlock:` at the end: the JVM detected a deadlock and lists the cycle.
- Threads in `SocketInputStream.read` inside an HTTP client: a dependency without a timeout.

Take **three dumps about ten seconds apart**: threads stuck in the same place across all three are the problem. Virtual threads (topic 2.3) appear with `jcmd <pid> Thread.dump_to_file -format=json threads.json`.

## 6. Heap dumps: "memory keeps growing"

If heap use climbs after every garbage collection until `OutOfMemoryError: Java heap space`, something keeps references it no longer needs: a static `Map` used as a cache without eviction, listeners never removed, an unbounded queue, or loading a whole table into memory.

```bash
jcmd <pid> GC.heap_info                       # quick view of heap use
jcmd <pid> GC.heap_dump /tmp/heap.hprof       # full dump (pauses the app; can be large)
# Capture automatically on the next crash:
JAVA_TOOL_OPTIONS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
```

Open the `.hprof` in **Eclipse Memory Analyzer (MAT)** or IntelliJ IDEA Ultimate and start with the **Leak Suspects** report and the **dominator tree**: the objects retaining the most memory, and the chain of references keeping them alive. Heap dumps contain customer data: treat them as sensitive, never attach them to tickets or chats, and delete them when done. (The starter disables the Actuator `heapdump` endpoint for this reason.)

For CPU problems, **Java Flight Recorder** is built into the JDK: `jcmd <pid> JFR.start duration=60s filename=rec.jfr`, then open it in JDK Mission Control. Phase 3 covers profiling in depth.

## 7. Debugging an application in Docker

Add the JDWP agent through an environment variable for a local container only:

```yaml
services:
  app:
    environment:
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ports:
      - "127.0.0.1:5005:5005"        # bind to localhost only
```

In IntelliJ IDEA create a **Remote JVM Debug** configuration for `localhost:5005` and set breakpoints as usual. Never enable remote debugging on a shared or production server: anyone who can reach the port can run arbitrary code in the JVM.

## Common mistakes

- Reading only the first line of a stack trace and missing the root `Caused by`.
- `catch (Exception e) { log.error(e.getMessage()); }` loses the stack trace; pass the exception as the last argument.
- Fixing a symptom (adding a retry, increasing a timeout, raising pool size) without finding why the resource was exhausted.
- Debugging by adding `System.out.println` everywhere and committing it.
- Taking one thread dump and drawing conclusions; take several.
- Leaving `MDC` values set on pooled threads, so the next request logs the wrong correlation id.
- Sharing heap dumps or logs that contain personal data.

## Practice

1. Take the stack trace from a failing test in your capstone, find the root cause and the first frame in your code, and explain it to a peer in two sentences.
2. Use a conditional breakpoint to stop on the 5,000th transfer of a load test, and an exception breakpoint to find where a swallowed exception is thrown.
3. Add the `CorrelationIdFilter` and structured logging to your capstone, then trace one request's log lines by its id.
4. Reproduce connection-pool exhaustion: set `spring.datasource.hikari.maximum-pool-size=2`, make an endpoint hold a transaction for 5 seconds, send 20 concurrent requests, take three thread dumps and identify the waiting threads.
5. Create a leak with a static `List` that grows on every request, capture a heap dump on `OutOfMemoryError`, and find the leak with MAT's dominator tree.
6. Revisit the [concurrency find-and-fix lab](../../../../labs/02-concurrency-find-and-fix/README.md) and diagnose its deadlock with a thread dump before reading the code.

## Key terms

Reproduce, hypothesis, root cause, stack trace, `Caused by`, `Suppressed`, frame, breakpoint, conditional breakpoint, exception breakpoint, logpoint, step over/into/out, evaluate expression, drop frame, SLF4J, log level, MDC, correlation id, structured logging, thread dump, `jcmd`, `jstack`, thread state (`RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`), connection-pool exhaustion, heap dump, `.hprof`, dominator tree, Eclipse MAT, Java Flight Recorder, JDWP, remote debugging.

## Further reading

- [IntelliJ IDEA documentation: Debug code](https://www.jetbrains.com/help/idea/debugging-code.html)
- [IntelliJ IDEA documentation: Breakpoints](https://www.jetbrains.com/help/idea/using-breakpoints.html)
- [Oracle Java SE 21 Troubleshooting Guide](https://docs.oracle.com/en/java/javase/21/troubleshoot/)
- [Oracle: The jcmd utility](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [Spring Boot reference: Logging (including structured logging)](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [SLF4J manual: Mapped Diagnostic Context](https://www.slf4j.org/manual.html#mdc)
- [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/)
