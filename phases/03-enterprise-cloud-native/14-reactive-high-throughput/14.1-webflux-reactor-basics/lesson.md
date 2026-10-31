## What you will be able to do

- Explain the event-loop model behind Spring WebFlux, and the one rule it imposes: never block.
- Work with `Mono` and `Flux`: create, transform, combine, handle errors and time out.
- Explain backpressure and hot versus cold publishers, and choose an overflow strategy.
- Call other services with `WebClient`, and stream data to clients with server-sent events.
- Test reactive code with `StepVerifier` and `WebTestClient`.

## Why this matters in a bank or fintech

Some workloads are mostly **waiting**: an FX service that asks three liquidity providers for a price, a gateway in front of slow partner banks, a dashboard that streams thousands of rate updates to traders and to the mobile app. Reactive Java was built for these: a handful of threads serving tens of thousands of open connections, with built-in flow control. It's also harder to write and debug than ordinary code, and since Java 21 virtual threads solve part of the same problem more simply (topic 14.3). Knowing both, and when each fits, is what interviewers for high-throughput roles probe.

## 1. The model: an event loop

Spring MVC (on Tomcat) traditionally gives each request a thread, which blocks while it waits for the database or another service. Spring **WebFlux** runs on **Reactor Netty** with a few **event-loop** threads (about one per CPU core). A request never owns a thread: when it waits for I/O, it registers a callback and the thread moves on to other requests.

The rule that follows: **never block an event-loop thread.** One `Thread.sleep`, JDBC call or `RestClient` call on it stalls every request that thread serves. If you must call blocking code, move it to a separate scheduler (`subscribeOn(Schedulers.boundedElastic())`), and know that you've given up some of the benefit. **BlockHound** detects blocking calls on non-blocking threads in tests.

## 2. Mono and Flux

**Project Reactor** gives you two types:

- `Mono<T>`: 0 or 1 value, then complete (or an error). A single HTTP response, one database row.
- `Flux<T>`: 0 to N values, possibly infinite. A query result, a stream of rate ticks.

They're **lazy**: building a pipeline does nothing until something **subscribes** (in WebFlux, the framework subscribes when it writes the response). They're also **declarative**: you describe what happens to each value.

```java
@GetMapping("/{base}-{quote}")
Mono<Rate> one(@PathVariable String base, @PathVariable String quote) {
    String pair = base + "-" + quote;
    return feed.current().filter(r -> r.pair().equals(pair)).next()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No rate for " + pair)));
}
```

Operators you'll use most:

| Operator | Does | Example |
| --- | --- | --- |
| `map` | Transform each value synchronously | rate → quote |
| `flatMap` | Transform each value into another `Mono`/`Flux`, **concurrently** | quote → insert it |
| `concatMap` | Like `flatMap`, but one at a time, **in order** | apply rate ticks in sequence |
| `filter`, `next`, `take` | Select values | the GBP-NGN ticks, the first one |
| `zipWith`, `Mono.zip` | Wait for several, combine | provider rate + our rate |
| `switchIfEmpty`, `defaultIfEmpty` | Handle "nothing" | 404 when no rate |
| `timeout`, `retryWhen(Retry.backoff(...))` | Resilience (topic 10.3) | 2 s on the provider call |
| `onErrorResume`, `onErrorMap` | Recover or translate errors | fallback quote |

Inside `map` you must not call anything that returns a `Mono`; that's what `flatMap` is for. Mixing them up is the most common beginner bug (you get a `Mono<Mono<T>>`, or a call that never runs because nothing subscribed to it).

## 3. WebClient: calling other services without blocking

The benchmark endpoint asks the FX provider and reads our own rate **at the same time**, and neither wait holds a thread:

```java
Mono<ProviderRate> fromProvider = provider.get()
        .uri("/provider/{base}-{quote}?delayMs={d}", base, quote, providerDelayMs)
        .retrieve().bodyToMono(ProviderRate.class)
        .timeout(Duration.ofSeconds(2));
return fromProvider.zipWith(rates.one(base, quote))          // both at once; neither blocks a thread
        .map(t -> new Quote(..., t.getT1().ask().max(t.getT2().ask()), ...));
```

With Spring Boot 4, the auto-configured `WebClient.Builder` comes from `spring-boot-starter-webclient` (as `RestClient.Builder` comes from `spring-boot-starter-restclient`, topic 10.3); add it if the builder bean is missing, as we had to.

## 4. Backpressure, and hot versus cold

**Backpressure** is the subscriber telling the publisher how much it can take (`request(n)`), so a fast producer can't drown a slow consumer. Reactor does this for you between operators. When the source can't slow down (a market feed doesn't wait for anyone), you choose an **overflow strategy**:

- `onBackpressureBuffer(n)`: queue up to n, then fail or drop.
- `onBackpressureDrop()`: discard what can't be delivered.
- `onBackpressureLatest()`: keep only the newest; ideal for prices, where an old rate is worthless.

A **cold** publisher starts from the beginning for each subscriber (a database query). A **hot** publisher emits whether or not anyone listens, and subscribers see only what comes after they join (a market feed). The rates service's feed is hot: one simulated feed, saved to the database on each tick, broadcast to every client through a **sink**:

```java
private final Sinks.Many<Rate> ticks = Sinks.many().multicast().directBestEffort();

running = Flux.interval(tick)
        .onBackpressureDrop()                              // if saving falls behind, skip ticks rather than queue them
        .concatMap(n -> current().concatMap(this::move))   // one tick at a time, in order
        .subscribe(rate -> ticks.tryEmitNext(rate),
                   error -> log.error("rate feed stopped", error));
```

**Choose the sink as carefully as the operator.** The first version used `Sinks.many().multicast().onBackpressureBuffer(256, false)`. On Kubernetes, the first client to open the stream received prices about **95 seconds old**: that sink **keeps what's emitted before anyone subscribes** (up to 256 elements) and replays it to the first subscriber. For prices that's exactly wrong. `directBestEffort()` has no warm-up buffer, and a slow subscriber misses ticks instead of holding up the others. A test now checks that a new subscriber's first ticks are newer than the moment it joined, and fails with the old sink.

## 5. Server-sent events

**Server-sent events (SSE)** keep an HTTP response open and push events down it: one-way, plain HTTP, automatic reconnection in browsers (`EventSource`), and they pass through proxies and gateways. For live rates that's simpler than WebSockets:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<ServerSentEvent<Rate>> stream(@RequestParam(required = false) String pair) {
    Flux<ServerSentEvent<Rate>> ticks = feed.ticks()
            .filter(r -> pair == null || r.pair().equals(pair))
            .onBackpressureLatest()
            .map(r -> ServerSentEvent.builder(r).event("rate").id(r.pair() + "@" + r.updatedAt().toEpochMilli()).build());
    Flux<ServerSentEvent<Rate>> heartbeats = Flux.interval(Duration.ofSeconds(15))   // keeps proxies from closing an idle stream
            .map(n -> ServerSentEvent.<Rate>builder().comment("keep-alive").build());
    return Flux.merge(ticks, heartbeats);
}
```

Through the ITL Pay gateway (`curl -N localhost:8080/api/rates/stream?pair=USD-NGN`):

```
id:USD-NGN@1793409425614
event:rate
data:{"pair":"USD-NGN","bid":1525.2475,"ask":1535.2475,"updatedAt":"2026-10-31T01:17:05.614116667Z"}
```

Each client gets `onBackpressureLatest`, so a slow mobile connection receives the newest rate rather than a growing backlog.

## 6. Testing reactive code

`StepVerifier` subscribes and checks what arrives, with a timeout, so tests don't hang:

```java
var events = http.get().uri("/rates/stream?pair=GBP-NGN").accept(MediaType.TEXT_EVENT_STREAM).exchange()
        .expectStatus().isOk()
        .returnResult(new ParameterizedTypeReference<ServerSentEvent<Rate>>() { })
        .getResponseBody();
StepVerifier.create(events.filter(e -> e.data() != null).take(3))
        .thenConsumeWhile(e -> e.data().pair().equals("GBP-NGN") && "rate".equals(e.event()))
        .expectComplete()
        .verify(Duration.ofSeconds(10));
```

`WebTestClient` drives the endpoints; in Spring Boot 4 a `@SpringBootTest` needs `@AutoConfigureWebTestClient` to get one. Two things we learned while writing these tests: define Testcontainers as **beans** (not static fields) when the app runs background work such as the feed, so the database isn't stopped while the application is still using it; and a stub provider (a tiny HTTP server in the test) is simpler than pointing the app at itself.

## Worked example: a live rates board for the mobile app

1. The app opens `GET /api/rates/stream` through the gateway; WebFlux holds the connection on an event loop, using no thread while idle.
2. Every half second the feed moves each pair, saves it with R2DBC (topic 14.2) and emits it into the sink; every connected app receives it.
3. A customer on a weak 3G connection falls behind; `onBackpressureLatest` sends them the newest rate when they're ready, not a minute of stale ones.
4. The customer taps "convert ₦250,000 to GBP"; `POST /api/quotes` reads the latest ask, stores a quote valid for 30 seconds in a reactive transaction, and returns it.
5. A heartbeat comment every 15 seconds keeps load balancers from closing quiet streams.

## Common mistakes

- Blocking on an event loop: JDBC, `RestClient`, `Thread.sleep`, `.block()`.
- `map` where `flatMap` is needed, or building a `Mono` that nobody subscribes to.
- `flatMap` where order matters (use `concatMap`).
- Unbounded buffers for fast hot sources.
- Swallowing errors in `subscribe` without a handler.
- Using WebFlux for a CRUD service that would be simpler, and as fast, on MVC with virtual threads (topic 14.3).

## Practice

1. Run the stack, open the stream with `curl -N`, and change `rates.tick-ms` to 100. What happens to the stream and to the database?
2. Add `GET /rates/stream?pairs=USD-NGN,GBP-NGN` and a `Flux.sample(Duration.ofSeconds(1))` option for slow clients.
3. Write a `StepVerifier` test with `withVirtualTime` for a `Flux.interval`-based component, so the test doesn't wait real seconds.
4. Make the provider call retry twice with backoff (`Retry.backoff`) and fall back to our own rate with `onErrorResume`; test both paths.
5. Add BlockHound to the rates tests and introduce a `Thread.sleep` in a handler: confirm the test fails, then remove it.

## Key terms

Reactive programming, Spring WebFlux, Reactor Netty, event loop, non-blocking I/O, Project Reactor, `Mono`, `Flux`, lazy, subscribe, operator, `map`, `flatMap`, `concatMap`, `zip`, backpressure, `request(n)`, overflow strategy, hot and cold publishers, sink, `WebClient`, server-sent events, `EventSource`, heartbeat, `StepVerifier`, `WebTestClient`, BlockHound, `Schedulers.boundedElastic`.

## Further reading

- [Spring Framework reference: Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor reference guide](https://projectreactor.io/docs/core/release/reference/)
- [Reactor: which operator do I need?](https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html)
- [Spring Framework reference: WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [MDN: Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [BlockHound](https://github.com/reactor/BlockHound)
