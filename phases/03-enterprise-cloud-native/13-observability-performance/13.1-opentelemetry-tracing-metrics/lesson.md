## What you will be able to do

- Explain traces, spans, context propagation and sampling, and why logs alone can't debug a distributed system.
- Instrument Spring Boot 4.1 services with OpenTelemetry and Micrometer, and export traces over OTLP.
- Carry trace context across HTTP, Kafka **and** a transactional outbox.
- Add business metrics with Micrometer: counters, timers and gauges.
- Read a trace to find where the time went.

## Why this matters in a bank or fintech

A customer calls: "I sent ₦25,000 an hour ago and it says pending." The request went through the gateway, transfers, accounts, an outbox, Kafka, fraud and back. Without tracing, an engineer greps five services' logs for a reference and guesses at the order. With tracing, they paste the trace id and see every step, how long it took, and where it failed, in one picture. Metrics answer the other question: is this one customer, or everyone?

## 1. Traces, spans and context

- A **trace** is the whole journey of one request. A **span** is one operation in it (an HTTP call, a database query, a Kafka send), with a start, a duration, attributes and a parent.
- **Context propagation** carries the trace id from service to service. Over HTTP it's the W3C **`traceparent`** header: `00-<trace id>-<parent span id>-<flags>`. Over Kafka, the same value goes in a record header.
- **Sampling** decides which traces are kept. Locally the starter keeps everything (`management.tracing.sampling.probability=1.0`); in production you might keep 10% plus every trace with an error (tail sampling in the OpenTelemetry Collector).

**OpenTelemetry** (OTel) is the vendor-neutral standard for traces, metrics and logs, and its **OTLP** protocol is what tools such as Tempo, Jaeger, Grafana Cloud, Datadog and Elastic all accept. In Spring, **Micrometer Observation** instruments the code once, and Micrometer Tracing's OTel bridge turns observations into OTel spans.

## 2. Spring Boot 4.1 setup

Two dependencies in each service:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

and a few properties (Spring Boot 4 renamed several of these from Boot 3):

```properties
management.tracing.sampling.probability=1.0
management.opentelemetry.tracing.export.otlp.endpoint=${OTLP_ENDPOINT:http://localhost:4318}/v1/traces
management.otlp.metrics.export.enabled=false          # metrics are scraped by Prometheus instead (topic 13.2)
management.opentelemetry.resource-attributes.deployment.environment.name=${ENVIRONMENT:local}
spring.kafka.template.observation-enabled=true         # Kafka sends and listeners create spans and carry context
spring.kafka.listener.observation-enabled=true
```

With that, incoming and outgoing HTTP (including `RestClient` and the Spring Cloud Gateway), Kafka, JDBC connection pools, the circuit breaker, scheduled tasks and Spring Modulith's module calls all create spans without code changes.

## 3. The outbox breaks the trace, unless you carry it

Kafka observation propagates context when a send happens **inside** a traced request. But with a transactional outbox (topic 11.3), the request only writes a row; the relay sends it later, from a scheduled task with its own, unrelated trace. The trace stops at the database.

The starter fixes this by storing the trace context **in the outbox row**:

```sql
ALTER TABLE outbox ADD COLUMN traceparent TEXT;
```

```java
/** The W3C traceparent of the current span, e.g. 00-4bf92f35...-00f067aa0ba902b7-01, or null outside a trace. */
private String currentTraceparent() {
    var context = tracer.currentTraceContext().context();
    if (context == null) {
        return null;
    }
    var carrier = new HashMap<String, String>();
    propagator.inject(context, carrier, HashMap::put);
    return carrier.get("traceparent");
}
```

and the relay **continues** that trace when it publishes:

```java
Span span = continueTrace(row).name("outbox publish").tag("transfer.reference", row.msgKey()).start();
try (Tracer.SpanInScope scope = tracer.withSpan(span)) {   // the Kafka send becomes part of the original request's trace
    acks.add(kafka.send(row.topic(), row.msgKey(), row.payload()));
} finally {
    span.end();
}
```

where `continueTrace` extracts the stored `traceparent` with Micrometer's `Propagator`. A test proves it: it sends `POST /transfers` with `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-...`, and asserts that the `TransferReserved` record on Kafka carries a `traceparent` header with the same trace id.

## 4. Reading a real trace

Here is one transfer through the Compose stack, from Tempo, with times from the first span:

```
     0.0 ms  gateway        http post /api/transfers/**
    11.3 ms  transfers      http post /transfers
    19.3 ms  transfers      circuit-breaker
    27.1 ms  accounts       http post /accounts/{id}/holds
    32.0 ms  accounts       Account > AccountController.hold(...)            (a Spring Modulith module)
    35.4 ms  accounts       Notification > ReservationNotifier.on(FundsReserved)
   482.0 ms  transfers      outbox publish
   483.9 ms  transfers      transfer-events send
   520.8 ms  fraud          transfer-events process
   522.3 ms  fraud          fraud-decisions send
   542.0 ms  notifications  transfer-events process
   546.0 ms  transfers      fraud-decisions process
  1018.1 ms  transfers      outbox publish                                    (TransferCleared)
  1037.6 ms  fraud          transfer-events process
  1047.5 ms  notifications  transfer-events process
```

(18 spans in 5 services; a few client spans are left out.) Read it the way you'd read it in an incident:

- The synchronous part, gateway to accounts and back, took about 40 ms.
- Then **nothing for 450 ms**, until "outbox publish". That's the relay's 500 ms polling interval: the price of the outbox's reliability, visible at a glance. If customers need faster confirmation, you'd shorten the interval or switch to change data capture (topic 11.3).
- Fraud answered 40 ms after the event left. Kafka itself isn't the slow part.

## 5. Business metrics with Micrometer

Technical metrics (requests, latency, heap) come free. Business metrics you add:

```java
meters.counter("itlpay.transfers", "state", to.name()).increment();   // business metric: transfers reaching each state
```

```java
// How long the oldest unpublished event has waited: 0 when the relay keeps up (topic 13.2)
Gauge.builder("itlpay.outbox.oldest.age", this, OutboxRelay::oldestUnpublishedSeconds)
        .baseUnit("seconds")
        .description("Age of the oldest event not yet published to Kafka")
        .register(meters);
```

In Prometheus these become `itlpay_transfers_total{state="CLEARED"}` and `itlpay_outbox_oldest_age_seconds`. Rules for good metrics:

- **Counters** for things that happen (transfers, failures), **timers** for durations (Micrometer gives count, sum and histogram buckets), **gauges** for current values (queue length, age of the oldest item).
- **Tags with few values**: `state` (5 values) is fine; `reference` or `accountId` would create a time series per transfer and take Prometheus down. That's called **high cardinality**; put such ids in traces and logs, not metric tags.
- Name by what's measured, with the unit: `outbox.oldest.age` in seconds.

## Worked example: "my transfer is still pending"

An illustrative scenario; the numbers measured on the starter are in the sections above.


1. The customer's app shows reference `OBS-TRACE`. Support finds the trace id in the transfers logs (topic 13.2) and opens it in Grafana's Tempo view.
2. The trace shows `TransferReserved` was published 480 ms after the request and fraud cleared it, but there's no second "outbox publish" span.
3. The dashboard shows `itlpay_outbox_oldest_age_seconds` climbing: the relay has stopped. The `OutboxStuck` alert has fired (topic 13.5).
4. The relay's spans show Kafka sends timing out; Kafka's disk is full. Once it's fixed, the relay drains the outbox and the trace for `OBS-TRACE` completes with `TransferCleared`.

## Common mistakes

- Tracing HTTP but not Kafka, or losing context at the outbox, so every trace stops in the middle.
- High-cardinality metric tags (references, user ids, raw URLs).
- 100% sampling in production at high volume, or sampling so low that errors are never kept.
- Logging trace ids nowhere, so traces and logs can't be joined.
- Creating spans manually for everything the framework already instruments.

## Practice

1. Start the stack, send a transfer, and find its trace in Grafana (Explore → Tempo → search by service `gateway`). Identify the longest gap and explain it.
2. Set `outbox.poll-ms=100`, rebuild transfers, and measure how the gap changes. What does it cost the database?
3. Add a timer `itlpay.saga.duration` from `REQUESTED` to a final state (store the start time; record on `CLEARED` or `FAILED`), and show its p95 in Prometheus.
4. Add a custom span around fraud's decision with an attribute `fraud.decision`, and find a rejected transfer by that attribute in Tempo.
5. Explain in five sentences why `accountId` belongs in a span attribute but not in a metric tag.

## Key terms

Observability, trace, span, parent span, context propagation, W3C trace context, `traceparent`, sampling, head sampling, tail sampling, OpenTelemetry, OTLP, OpenTelemetry Collector, Micrometer, Observation, Micrometer Tracing, Tempo, Jaeger, counter, timer, gauge, histogram, tag, cardinality, business metric.

## Further reading

- [OpenTelemetry: observability primer](https://opentelemetry.io/docs/concepts/observability-primer/)
- [Spring Boot reference: tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot reference: OpenTelemetry support](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Micrometer documentation: concepts](https://docs.micrometer.io/micrometer/reference/concepts.html)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [Spring for Apache Kafka: observation](https://docs.spring.io/spring-kafka/reference/kafka/micrometer.html)
