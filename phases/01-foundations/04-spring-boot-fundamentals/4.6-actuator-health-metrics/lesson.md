## What you will be able to do

- Add Spring Boot Actuator and expose the right endpoints, securely.
- Use health checks, health groups, and liveness/readiness probes, and write a custom `HealthIndicator`.
- Record business and technical metrics with Micrometer (counters, timers, gauges, distribution summaries) and expose them to Prometheus.
- Use the `info`, `loggers`, `env`, `configprops`, `conditions`, `threaddump` and `heapdump` endpoints for diagnosis, and know which ones must never be public.

## Why this matters in a bank or fintech

When a payment service misbehaves, operations teams need answers in seconds: is it up, is it ready for traffic, is the database reachable, how many transfers failed in the last five minutes, how slow is the card switch? Actuator gives every Spring Boot service the same standard answers. Load balancers and Kubernetes use its health endpoints to route traffic, Prometheus scrapes its metrics, and Grafana turns them into the dashboards that on-call engineers watch. Module 13 builds full observability; this topic gives you the foundation every service needs from day one.

## 1. Adding Actuator

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Endpoints live under `/actuator`. By default, over HTTP only `health` is exposed. You choose what else to expose:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized      # never "always" on a public endpoint
      show-components: when-authorized
  server:
    port: 8081                           # optional: a separate management port, not exposed publicly
```

| Endpoint | Purpose | Expose publicly? |
| --- | --- | --- |
| `health` | Up/down status, plus probes | Yes (summary only) |
| `info` | Build version, Git commit, Java and process details | Usually internal |
| `metrics` | Browse metric names and values | Internal |
| `prometheus` | Metrics in Prometheus format | Internal (scraped by Prometheus) |
| `loggers` | View and **change log levels at runtime** | Internal, secured |
| `env`, `configprops` | Effective configuration (values sanitised) | Internal, secured |
| `conditions`, `beans`, `mappings` | Auto-configuration report, beans, URL mappings | Internal, secured |
| `threaddump` | Thread dump (topic 2.2) | Internal, secured |
| `heapdump` | Downloads a heap dump **containing all data in memory** | **Never public.** Prefer disabling in production |
| `shutdown` | Stops the app (disabled by default) | Never |

Exposed sensitive endpoints are a well-known attack path: a public `heapdump` can leak session tokens, passwords and customer data. Expose the minimum, put management on a separate port that only the internal network can reach, and secure it with Spring Security (Module 6).

## 2. Health

```bash
curl -s localhost:8080/actuator/health
{"status":"UP","groups":["liveness","readiness"]}
```

With details shown to an authorised caller:

```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "free": 51234567890, "threshold": 10485760 } },
    "ping":      { "status": "UP" },
    "redis":     { "status": "UP", "details": { "version": "8.0.2" } }
  }
}
```

Spring Boot auto-registers **health indicators** for what it finds: databases, Redis, MongoDB, disk space, mail servers, and more. The overall status is the worst component status (`DOWN` beats `OUT_OF_SERVICE` beats `UP`), and `DOWN` returns HTTP 503.

### Liveness and readiness

Platforms ask two different questions:

- **Liveness**: "Is this process healthy, or should it be restarted?" `/actuator/health/liveness`
- **Readiness**: "Should it receive traffic right now?" `/actuator/health/readiness`

Spring Boot 4 enables these probe groups by default. The distinction matters:

- If the database is down, the service should report **not ready** (stop sending it requests) but stay **live**: restarting it won't fix the database, and restarting every instance at once makes the outage worse.
- During startup (Flyway migrations, cache warm-up) and graceful shutdown, readiness is `OUT_OF_SERVICE`.

So: **don't put external dependencies in the liveness group.** Add the critical ones to readiness instead:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db,redis
```

Load balancers on a VPS (Nginx, HAProxy), AWS ALB target groups, Azure App Service health checks, GCP Cloud Run and Kubernetes probes all point at these endpoints. The deployment guide in this phase shows each.

### Custom health indicators

```java
@Component("nibss")
public class NibssHealthIndicator implements HealthIndicator {
    private final NibssClient client;

    public NibssHealthIndicator(NibssClient client) { this.client = client; }

    @Override
    public Health health() {
        try {
            Duration latency = client.ping();                  // a cheap, side-effect-free call
            return Health.up().withDetail("latencyMs", latency.toMillis()).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();   // no secrets or URLs
        }
    }
}
```

Should a partner outage make your service `DOWN`? Usually **not**: you can still serve balance enquiries and queue transfers. Report it as a component and alert on it, but think carefully before adding it to readiness. Health checks are called often; keep them fast and cached if the check is expensive.

## 3. Metrics with Micrometer

Micrometer is a vendor-neutral metrics API ("SLF4J for metrics"). Spring Boot auto-configures a `MeterRegistry` and many built-in metrics:

- **HTTP**: `http.server.requests` (count, latency, by URI, method, status, outcome)
- **JVM**: memory per area, GC pauses, threads, classes loaded (topic 2.4)
- **Database pool**: `hikaricp.connections.active`, `.pending`, `.timeout` (topic 3.5)
- **System**: CPU usage, uptime, file descriptors
- **Tomcat**, caches, executors, Spring Data repository calls, and more

Add the Prometheus registry to expose them in Prometheus's text format at `/actuator/prometheus`:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```text
http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="201",uri="/api/v1/transfers"} 18234
http_server_requests_seconds_sum{method="POST",outcome="SUCCESS",status="201",uri="/api/v1/transfers"} 2511.8
hikaricp_connections_pending{pool="HikariPool-1"} 0
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 2.18103808E8
```

### Business metrics

Technical metrics tell you the service is alive; business metrics tell you it is doing its job. Record them yourself:

```java
@Service
public class TransferService {
    private final Counter completed;
    private final Counter rejected;
    private final Timer switchLatency;
    private final DistributionSummary amounts;

    public TransferService(MeterRegistry registry /* , other dependencies */) {
        this.completed = Counter.builder("bank.transfers").tag("outcome", "completed")
                .description("Transfers completed").register(registry);
        this.rejected = Counter.builder("bank.transfers").tag("outcome", "rejected").register(registry);
        this.switchLatency = Timer.builder("bank.switch.latency")
                .publishPercentileHistogram()          // lets Prometheus compute p95/p99 across instances
                .register(registry);
        this.amounts = DistributionSummary.builder("bank.transfer.amount").baseUnit("naira").register(registry);
    }

    public TransferReceipt transfer(TransferRequest r) {
        try {
            TransferReceipt receipt = switchLatency.recordCallable(() -> nip.send(r));
            completed.increment();
            amounts.record(r.amount().doubleValue());   // metrics are approximate by nature; the ledger is the record
            return receipt;
        } catch (TransferRejectedException e) {
            rejected.increment();
            throw e;
        } catch (Exception e) {
            throw new TransferFailedException(r.clientReference(), e);
        }
    }
}
```

| Meter | Measures | Example |
| --- | --- | --- |
| `Counter` | Things that only go up | Transfers completed, OTPs sent, fraud flags raised |
| `Timer` | How long something takes, and how often | Card switch latency, statement generation time |
| `Gauge` | A current value that goes up and down | Payment jobs pending in the queue, cache size |
| `DistributionSummary` | Distribution of non-time values | Transfer amounts, file sizes |

`@Timed` and `@Counted` annotations (with the AOP starter) are a shortcut for methods. Spring's **Observation API** (`@Observed`) records metrics and traces together; Module 13 uses it with OpenTelemetry.

### Tags and cardinality

Tags (labels) let you slice a metric: by outcome, channel, partner bank. Every **unique combination** of tag values creates a separate time series. Never tag with unbounded values such as account numbers, customer IDs, transaction references or raw URLs: millions of series will overwhelm Prometheus. Tag with small, fixed sets (`channel` = MOBILE/USSD/POS/WEB). Spring already uses URI *templates* (`/api/v1/accounts/{number}`) rather than raw paths for this reason.

## 4. Other useful endpoints

### `info`

```yaml
management:
  info:
    env.enabled: true
    java.enabled: true
    os.enabled: true
    process.enabled: true
info:
  app:
    name: transfer-service
    team: payments
```

Add the `build-info` goal of the Spring Boot Maven plugin and the `git-commit-id-maven-plugin` to include the build version and Git commit, so "which version is running in UAT?" has an instant answer. Spring Boot 4.1 adds more process details (uptime, start time, time zone).

### `loggers`: change log levels without a restart

```bash
curl -s localhost:8081/actuator/loggers/org.hibernate.SQL
curl -X POST localhost:8081/actuator/loggers/ng.itlearning.bank.transfer \
     -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

Turning on `DEBUG` for one package during an incident, then off again, is far better than redeploying. It is also a reason to secure the endpoint.

### Diagnosis endpoints

`threaddump` and `heapdump` give you topic 2.2's and 2.4's diagnostics over HTTP, `conditions` gives you topic 4.1's report, `mappings` lists every URL, and `env`/`configprops` show effective configuration with sensitive values masked as `******`. Keep `management.endpoint.env.show-values` and `configprops.show-values` at their safe defaults (`never` or `when-authorized`).

## Worked example: a production-ready baseline

```yaml
management:
  server.port: 8081
  endpoints.web.exposure.include: health,info,prometheus,loggers
  endpoint:
    health:
      show-details: when-authorized
      group.readiness.include: readinessState,db
    heapdump.access: none            # disable the heap dump endpoint entirely
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
  observations:
    key-values:
      region: ${REGION:lagos}
```

With this, every instance:

- answers liveness and readiness probes on the internal management port;
- exposes Prometheus metrics with `application` and `environment` tags so one Grafana dashboard works for all services;
- reports its version via `info`;
- allows log levels to be changed during incidents by authorised operators;
- cannot leak memory contents through `heapdump`.

Build a small Grafana dashboard (Module 13 does this properly): transfers per minute by outcome, p95 switch latency, HTTP 5xx rate and Hikari pending connections. Those four panels catch most payment incidents.

## Common mistakes

- `exposure.include: "*"` on a public port.
- `show-details: always` publicly (reveals database type, versions and disk sizes).
- External dependencies in the liveness group, causing restart storms.
- Expensive health checks run on every probe.
- High-cardinality tags (account numbers, references).
- Only technical metrics, no business metrics.

## Practice

1. Add Actuator to your service, expose `health`, `info`, `metrics` and `prometheus` on port 8081, and call each with `curl`.
2. Stop the PostgreSQL container and watch `/actuator/health/readiness` go `DOWN` while `/actuator/health/liveness` stays `UP`. Start it again.
3. Add `bank.transfers` counters by outcome and a `bank.switch.latency` timer, generate traffic with a small loop, and find the values in `/actuator/prometheus`.
4. Run Prometheus and Grafana with Docker Compose, scrape your service, and build a panel showing transfers per minute by outcome.

## Key terms

Actuator, endpoint, exposure, management port, health indicator, health group, liveness, readiness, `OUT_OF_SERVICE`, Micrometer, `MeterRegistry`, counter, timer, gauge, distribution summary, percentile histogram, tag, cardinality, Prometheus, `info` endpoint, `loggers` endpoint, sanitisation, heap dump exposure.

## Further reading

- [Spring Boot reference: Production-ready features (Actuator)](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Spring Boot reference: Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot reference: Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Micrometer documentation: Concepts](https://docs.micrometer.io/micrometer/reference/concepts.html)
- [Kubernetes: Configure liveness, readiness and startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Prometheus: Metric types](https://prometheus.io/docs/concepts/metric_types/)
