## What this module gives you

One week on high-throughput Java and the choice every Java team now faces: **reactive** (Spring WebFlux, Project Reactor, R2DBC) or **virtual threads** (Spring MVC on Java 21+, with ordinary blocking code). You build a streaming FX-rates service the reactive way (topics 14.1 and 14.2), then benchmark it against the same endpoint on virtual threads and on classic platform threads (topic 14.3).

The [ITL Pay starter](../../../templates/itl-pay-starter/README.md) now has a `rates` service (WebFlux, `WebClient`, R2DBC, server-sent events, tested with `StepVerifier` and Testcontainers) and a `rates-mvc` twin for the comparison, with a k6 benchmark and a Compose file that gives each variant the same CPU and memory. The numbers in topic 14.3 come from running it, including the runs that went wrong and what they taught us.

**Weekly rhythm (20–25 hours):** read the lessons and code along (30%), videos (10%), topic quizzes (5%), the module project (20%) and your capstone system's `v0.5` (35%).

**Exit check for Module 14:** the three topic quizzes attempted (aim for 70%+), one module project below, and capstone release `v0.5` tagged with a benchmark report and ADR 9.

## Capstone step: ITL Pay `v0.5`

Build on your `v0.4` and the [starter's Module 14 code](../../../templates/itl-pay-starter/README.md), and deliver:

- **Rates:** a reactive rates service with the latest rates, a server-sent-events stream with a sensible backpressure strategy, and quotes valid for a short time, stored with R2DBC; routed through your gateway and traced.
- **Integration:** transfers in a foreign currency use a quote: the transfer request carries the quote id, and transfers checks it hasn't expired before reserving funds.
- **Benchmark:** the same endpoint on WebFlux and on MVC with virtual threads, under the same limits, at loads below and above capacity; a report with throughput, latency, errors, memory and threads, and what happened at overload.
- **Protection:** whichever model you keep, a limit on work in progress (a bulkhead, a semaphore or a bounded queue) so overload sheds load instead of exhausting memory, proved with a load test.
- **Docs:** ADR 9: which model ITL Pay uses where, and why, from your own numbers.

## Module projects

Pick **at least one**, in addition to your capstone step.

### Project 14.A: Non-blocking rates/quotes streaming endpoint

Build a service that aggregates prices from three simulated providers with different latencies and failure rates, streams the best bid and ask per pair as server-sent events, and degrades gracefully when a provider is slow or down (timeouts, fallbacks). **Acceptance:** `StepVerifier` tests with virtual time for the timing logic; a browser page with `EventSource` showing live prices; a slow client receives only the latest price; one provider failing doesn't stop the stream.

### Project 14.B: Benchmark: MVC + virtual threads vs WebFlux

Write the same small service three ways (MVC with platform threads, MVC with virtual threads, WebFlux), each calling a slow dependency and a database, and benchmark them with k6 under identical CPU and memory limits. **Acceptance:** a report with a table per load level (throughput, p50/p95/p99, errors, memory, live threads), the overload behaviour of each, a flame graph or JFR finding for one of them, and a recommendation that a tech lead could act on.
