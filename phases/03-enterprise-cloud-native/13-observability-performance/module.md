## What this module gives you

Two weeks on seeing what ITL Pay is doing and making it faster with evidence: traces that follow a transfer through HTTP **and** Kafka, metrics, dashboards and structured logs (week 32), then profiling, caching, databases under load, load tests, SLOs and incident response (week 33).

Everything runs locally with Docker Compose: **OpenTelemetry** through Spring Boot 4.1's `spring-boot-starter-opentelemetry`, **Prometheus**, **Grafana** and **Tempo** (in Grafana's all-in-one `otel-lgtm` image), **Redis 8**, **Java Flight Recorder** and **k6**. The [ITL Pay starter](../../../templates/itl-pay-starter/README.md) now has every lesson's code, and the numbers in these lessons come from running it: one trace spanning 18 operations in 5 services, a load test that exposed an outbox relay 45 seconds behind, a circuit breaker opened by lock contention that Flight Recorder then pinpointed, and a library lock nobody expected.

**Weekly rhythm (20–25 hours):** read the lessons and code along (30%), videos (10%), topic quizzes (5%), the module project (20%) and your capstone system's `v0.4` (35%).

**Exit check for Module 13:** the five topic quizzes attempted (aim for 70%+), one module project below, and capstone release `v0.4` tagged with a load-test report, SLOs, ADR 8 and a runbook.

## Capstone step: ITL Pay `v0.4`

Build on your `v0.3` and the [starter's Module 13 code](../../../templates/itl-pay-starter/README.md), and deliver:

- **Traces:** one trace per transfer across the gateway, every service, the outbox and Kafka, including your ledger; a screenshot of a whole saga in Tempo or Jaeger.
- **Metrics:** business metrics (transfers by state, amounts by outcome, outbox age, saga duration), RED metrics per service, and a Grafana dashboard provisioned from the repository.
- **Logs:** structured JSON logs with trace ids, and the query that jumps from a trace to its logs.
- **SLOs and alerts:** two SLOs written down (availability and latency of `POST /api/transfers`), burn-rate alerts, and alerts on the outbox and consumer lag, each with a runbook.
- **Load test:** a k6 test at your expected peak and twice it, with a report: throughput, latency percentiles, errors, the bottleneck you found, the change you made and the before-and-after numbers.
- **Profiling:** a Flight Recorder recording under load, with one finding written up.
- **Caching:** at least one cache with a written policy (what, TTL, how it's invalidated, what happens when Redis is down), and its hit ratio on the dashboard.
- **Docs:** ADR 8 (observability choices), the runbook for each paging alert, and a short postmortem of one problem you found under load.

## Module projects

Pick **at least one**, in addition to your capstone step.

### Project 13.A: End-to-end tracing across your services

Instrument your Phase 2 capstone (or two services of it) with OpenTelemetry, propagate context across HTTP and a message broker, and add custom spans and attributes for the business steps. **Acceptance:** one trace shows the whole user journey; a deliberate slow query shows up as the obvious long span; a test asserts that the trace id survives the asynchronous hop.

### Project 13.B: Grafana dashboard with alert rules

Build a dashboard for a service you own: RED metrics, saturation (pools, threads, heap), business metrics and SLO burn. Provision it and the alert rules from files. **Acceptance:** a load test makes the right panels move; you trigger each alert on purpose (stop a dependency, slow a query) and show it firing and resolving.

### Project 13.C: Memory-leak hunt and latency-reduction lab

Take a small Spring Boot service, add a deliberate leak (an unbounded static map keyed by request) and a slow path (an N+1 query). Find both **only** with tools: heap histograms and a heap dump in Eclipse MAT for the leak, Flight Recorder or async-profiler and SQL logs for the latency. **Acceptance:** a write-up with the evidence at each step, the fix, and before-and-after numbers from a k6 run.
