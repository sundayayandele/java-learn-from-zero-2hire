## What you will be able to do

- Call third-party HTTP APIs with Spring's **`RestClient`** and declarative **HTTP interface** clients (`@HttpExchange`).
- Set **connect and read timeouts** on every outbound call, and explain why the defaults are dangerous.
- Retry **only** what is safe to retry, with exponential backoff and jitter, using Spring Framework 7's `@Retryable` or Resilience4j.
- Protect your service with a **circuit breaker** and a **concurrency limit** (bulkhead).
- Receive **webhooks** securely: verify HMAC signatures, de-duplicate events and handle out-of-order delivery.
- Test all of this deterministically with **WireMock**.

## Why this matters in a bank or fintech

A fintech backend is mostly integrations: payment gateways (Paystack, Flutterwave, Stripe), card processors, the NIP switch, BVN and NIN verification, credit bureaus, SMS and email providers, FX feeds. Each of them will be slow or down at some point. Without timeouts, one slow provider ties up every request thread and takes your whole service down with it. Without careful retries, a retried "charge card" call charges the customer twice. And an unverified webhook endpoint lets anyone tell your system that a payment succeeded. Integration resilience is one of the most common topics in fintech system-design interviews.

## 1. RestClient and HTTP interfaces

`RestClient` is Spring's synchronous HTTP client (it replaces `RestTemplate`; on Spring Boot 4 it comes with `spring-boot-starter-restclient`):

```java
@Configuration
class GatewayClientConfig {

    @Bean
    RestClient gatewayRestClient(RestClient.Builder builder, GatewayProperties props) {
        return builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.secretKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(
                        HttpClientSettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(2))
                                .withReadTimeout(Duration.ofSeconds(5))))
                .build();
    }
}
```

An **HTTP interface** declares the remote API as a Java interface; Spring generates the implementation:

```java
@HttpExchange("/transaction")
interface PaymentGatewayClient {

    @PostExchange("/initialize")
    InitializeResponse initialize(@RequestBody InitializeRequest request);

    @GetExchange("/verify/{reference}")
    VerifyResponse verify(@PathVariable String reference);
}

@Bean
PaymentGatewayClient paymentGatewayClient(RestClient gatewayRestClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(gatewayRestClient))
            .build()
            .createClient(PaymentGatewayClient.class);
}
```

Spring Framework 7 can also register groups of HTTP interfaces for you with `@ImportHttpServices(group = "gateway", types = PaymentGatewayClient.class)`, and Spring Boot 4 then configures each group from properties such as `spring.http.serviceclient.gateway.base-url`.

Error handling: by default a 4xx or 5xx response throws `HttpClientErrorException` or `HttpServerErrorException`. Translate them at the boundary into your own exceptions (`PaymentDeclinedException`, `GatewayUnavailableException`) so the rest of your code never depends on the HTTP library.

## 2. Timeouts on every call

| Timeout | Guards against | Typical value |
| --- | --- | --- |
| Connect | Host down or unreachable | 1–3 seconds |
| Read (response) | Host accepts the connection but answers slowly | Just above the provider's p99 latency, often 3–10 seconds |
| Overall deadline | The whole operation including retries | Less than your own client's timeout |

Many HTTP clients default to **no** read timeout. A thread waiting forever on a hung provider is a thread that cannot serve anyone else; with enough of them, your service stops. Set timeouts globally (`spring.http.clients.connect-timeout`, `spring.http.clients.read-timeout`) and per client when one provider is known to be slower.

## 3. Retries: only when safe

Retry when the failure is **transient** and the operation is **idempotent**:

| Retry | Do not retry |
| --- | --- |
| Connect timeout, connection refused/reset | 400, 401, 403, 404, 409, 422 (the request is wrong or rejected) |
| 502, 503, 504, 429 (honour `Retry-After`) | A non-idempotent call that may have reached the provider (read timeout on "charge card") |
| Read timeout on a `GET` or an idempotent call with an idempotency key | Anything after your own deadline has passed |

For a payment initialisation, send an **idempotency key or your own unique reference** to the provider. Then a retry after a read timeout is safe, because the provider recognises the duplicate. If the provider offers no such mechanism, do not retry blindly: **verify** the status first (`GET /transaction/verify/{reference}`).

Always use **exponential backoff with jitter** (for example 200 ms, 400 ms, 800 ms, each ±25% random), a small attempt limit (2–3 retries), and a budget so retries cannot multiply load during an outage.

Spring Framework 7 has retry built in:

```java
@Configuration
@EnableResilientMethods
class ResilienceConfig { }

@Service
class FxRateClient {

    @Retryable(includes = {ResourceAccessException.class, HttpServerErrorException.class},
               maxRetries = 3, delay = 200, multiplier = 2, jitter = 50, maxDelay = 2000)
    public Rates latest(String base) {
        return fxApi.latest(base);   // GET: safe to retry
    }
}
```

`@ConcurrencyLimit(10)` on a method caps how many threads may call it at once, which is a simple **bulkhead**: a slow provider can then block at most ten threads.

## 4. Circuit breakers

When a provider is clearly down, retrying every request just adds load and latency. A **circuit breaker** watches failures and short-circuits calls:

- **Closed**: calls go through; failures and slow calls are counted in a sliding window.
- **Open**: after the failure rate passes a threshold (say 50% of the last 20 calls), calls fail immediately with a fallback for a wait period (say 30 seconds).
- **Half-open**: a few trial calls are let through; if they succeed, the breaker closes; if not, it opens again.

Resilience4j (with `resilience4j-spring-boot4`) provides it:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      gateway:
        sliding-window-size: 20
        failure-rate-threshold: 50
        slow-call-duration-threshold: 3s
        slow-call-rate-threshold: 80
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        ignore-exceptions: com.example.payments.PaymentDeclinedException   # business errors are not failures
```

```java
@CircuitBreaker(name = "gateway", fallbackMethod = "gatewayDown")
public InitializeResponse initialize(InitializeRequest request) {
    return client.initialize(request);
}

private InitializeResponse gatewayDown(InitializeRequest request, Throwable cause) {
    throw new GatewayUnavailableException(cause);   // mapped to 503 + Retry-After
}
```

Note `ignore-exceptions`: a declined card is a correct answer from a healthy provider and must not open the breaker. Expose breaker state through Actuator (`/actuator/health`, metrics `resilience4j.circuitbreaker.state`) so operations can see it.

Order of wrapping, outermost first: **bulkhead → circuit breaker → retry → timeout → call**. Retries live inside the breaker so that a failed attempt series counts against it.

## 5. Webhooks

Providers tell you about asynchronous outcomes (payment succeeded, transfer reversed, KYC verified) by calling **your** endpoint. Treat every webhook as hostile until proven otherwise.

1. **Verify the signature** over the **raw** request body before parsing it. Paystack-style providers send `x-paystack-signature: HMAC-SHA512(rawBody, secretKey)`:

```java
@PostMapping("/api/v1/webhooks/gateway")
ResponseEntity<Void> receive(@RequestHeader("x-paystack-signature") String signature,
                             @RequestBody byte[] rawBody) {
    if (!signatures.isValid(rawBody, signature)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    GatewayEvent event = json.readValue(rawBody, GatewayEvent.class);
    webhookInbox.accept(event);          // store and process asynchronously
    return ResponseEntity.ok().build();  // acknowledge quickly
}

boolean isValid(byte[] body, String signatureHex) {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(secret, "HmacSHA512"));
    byte[] expected = mac.doFinal(body);
    return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signatureHex));   // constant-time
}
```

2. **De-duplicate** by the provider's event ID (a unique constraint on an `inbound_event` table). Delivery is at least once.
3. **Do not trust the payload's claims alone.** For money, call the provider's verify endpoint and check the amount, currency and reference against what you initialised before marking a payment successful.
4. **Tolerate out-of-order and late events**: apply state transitions only forward (`PENDING → SUCCEEDED` yes, `SUCCEEDED → PENDING` no).
5. **Acknowledge fast** (within a few seconds) and process in the background; providers retry slow or failed deliveries.
6. Optionally restrict the endpoint to the provider's published IP ranges, and include a timestamp check against replayed old messages when the provider supports it.

## 6. Testing with WireMock

WireMock runs a fake HTTP server in your tests so you can script any behaviour of the provider, including the bad ones:

```java
@SpringBootTest
@EnableWireMock(@ConfigureWireMock(name = "gateway", baseUrlProperties = "gateway.base-url"))
class PaymentGatewayClientTest {

    @InjectWireMock("gateway") WireMockServer gateway;
    @Autowired PaymentService payments;

    @Test
    void retriesOnce503ThenSucceeds() {
        gateway.stubFor(post("/transaction/initialize").inScenario("flaky")
                .whenScenarioStateIs(STARTED).willReturn(serviceUnavailable())
                .willSetStateTo("recovered"));
        gateway.stubFor(post("/transaction/initialize").inScenario("flaky")
                .whenScenarioStateIs("recovered").willReturn(okJson("""
                        {"status": true, "data": {"authorization_url": "https://checkout.test/abc", "reference": "pay_1"}}""")));

        assertThat(payments.initialize(order()).checkoutUrl()).isEqualTo("https://checkout.test/abc");
        gateway.verify(2, postRequestedFor(urlEqualTo("/transaction/initialize")));
    }

    @Test
    void readTimeoutIsNotRetriedWithoutIdempotency() {
        gateway.stubFor(post("/transaction/charge").willReturn(ok().withFixedDelay(6_000)));
        assertThatThrownBy(() -> payments.chargeSavedCard(order())).isInstanceOf(GatewayUnavailableException.class);
        gateway.verify(1, postRequestedFor(urlEqualTo("/transaction/charge")));
    }
}
```

(`@EnableWireMock` comes from the `wiremock-spring-boot` integration; you can also start a `WireMockServer` yourself in `@BeforeAll` and set the base URL with `@DynamicPropertySource`.)

## Common mistakes

- No read timeout, so one slow provider exhausts the thread pool.
- Retrying 4xx responses, or non-idempotent calls after a read timeout.
- Retries without backoff, or retries at several layers (client, service and gateway) multiplying load.
- Counting business errors (card declined) as circuit-breaker failures.
- Parsing the webhook JSON before verifying the signature, or comparing signatures with `String.equals` (timing attack).
- Marking a payment successful because a webhook said so, without verifying amount and reference.
- Tests that call the real sandbox, so the build fails when the provider is down.

## Practice

1. Build an HTTP interface client for the free Frankfurter FX API with timeouts, `@Retryable` on 5xx and connect failures, and a circuit breaker; write WireMock tests for a timeout, a 503 burst that opens the breaker, and a 400 that is not retried.
2. Implement a webhook endpoint with HMAC-SHA512 verification and event de-duplication; test a valid event, a tampered body, and the same event delivered twice.
3. Draw the sequence diagram for "initialise payment → customer pays → webhook → verify → credit wallet" and mark every point where a retry, timeout or duplicate can occur.

## Key terms

`RestClient`, HTTP interface, `@HttpExchange`, `HttpServiceProxyFactory`, `@ImportHttpServices`, connect timeout, read timeout, deadline, transient failure, retry, exponential backoff, jitter, retry budget, `@Retryable`, `@EnableResilientMethods`, `@ConcurrencyLimit`, bulkhead, circuit breaker (closed, open, half-open), fallback, webhook, HMAC, constant-time comparison, at-least-once delivery, WireMock, scenario.

## Further reading

- [Spring Framework reference: REST Clients (RestClient and HTTP interfaces)](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring Framework reference: Resilience features (@Retryable, @ConcurrencyLimit)](https://docs.spring.io/spring-framework/reference/core/resilience.html)
- [Resilience4j documentation: CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [AWS Builders' Library: Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [Paystack documentation: Webhooks](https://paystack.com/docs/payments/webhooks/)
- [WireMock documentation: Stubbing](https://wiremock.org/docs/stubbing/)
