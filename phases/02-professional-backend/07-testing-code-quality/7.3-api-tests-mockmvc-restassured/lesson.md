## What you will be able to do

- Test the web layer in isolation with **`@WebMvcTest`**, **MockMvc** and the AssertJ-based **`MockMvcTester`**.
- Test the whole running application over HTTP with Spring Framework 7's **`RestTestClient`** and with **REST Assured**.
- Assert on status, headers, JSON (with JSONPath) and Problem Details, including security cases.
- Write **contract tests** so a provider and its consumers cannot drift apart.
- Decide which API tests belong at which level of the test pyramid.

## Why this matters in a bank or fintech

Your API is what partners, mobile apps and auditors see. A changed field name, a missing `Location` header, or a 500 instead of a 422 breaks integrations even when the business logic is correct. API tests are the executable specification of that contract: they prove that validation, error formats, security rules and serialisation (money as strings, dates in ISO 8601) behave exactly as documented, on every build.

## 1. Choosing the level

| Level | Tool | Starts | Use for |
| --- | --- | --- | --- |
| Web slice | `@WebMvcTest` + `MockMvc`/`MockMvcTester` | Controllers, advice, converters, security filters; services are mocks | Validation, status codes, JSON shape, error mapping, URL security |
| Full app, mock server | `@SpringBootTest` + `@AutoConfigureMockMvc` | Whole context, no real HTTP | End-to-end flows through real services and Testcontainers DB |
| Full app, real HTTP | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` or REST Assured | Embedded server on a random port | Smoke tests, filters and servlet behaviour, black-box API checks |
| Deployed environment | REST Assured suite against staging | Nothing locally | Post-deployment smoke tests in the pipeline |

Most API tests should be web-slice tests (fast); a handful of full-application tests prove the wiring.

## 2. `@WebMvcTest` with MockMvc

```java
@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class TransferControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean TransferService transfers;

    @Test
    void createsTransferAndReturnsLocation() throws Exception {
        when(transfers.start(any())).thenReturn(completedTransfer("trf_1"));

        mvc.perform(post("/api/v1/transfers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transfers:write")))
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccount": "0123456789", "destinationAccount": "9876543210",
                                 "amount": "2500.00", "currency": "NGN"}"""))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", endsWith("/api/v1/transfers/trf_1")))
           .andExpect(jsonPath("$.amount").value("2500.00"))     // money serialised as a string
           .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejectsInvalidAccountNumberWithProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/transfers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transfers:write")))
                        .header("Idempotency-Key", "k-2").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccount": "12AB", "destinationAccount": "9876543210", "amount": "1.00", "currency": "NGN"}"""))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")))
           .andExpect(jsonPath("$.errors[0].field").value("sourceAccount"));
        verifyNoInteractions(transfers);
    }
}
```

`@WebMvcTest` (in Spring Boot 4 it comes from `spring-boot-starter-webmvc-test`) loads only web components, so it starts in well under a second. Mock the service layer with `@MockitoBean`.

### `MockMvcTester`: AssertJ style

`MockMvcTester` wraps MockMvc with fluent AssertJ assertions and no checked exceptions:

```java
@Autowired MockMvcTester mvc;

@Test
void insufficientFundsIs422() {
    when(transfers.start(any())).thenThrow(new InsufficientFundsException(new BigDecimal("100.00")));

    assertThat(mvc.post().uri("/api/v1/transfers")
                  .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transfers:write")))
                  .header("Idempotency-Key", "k-3")
                  .contentType(MediaType.APPLICATION_JSON).content(validTransferJson()))
            .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
            .bodyJson()
            .extractingPath("$.type").asString().endsWith("/insufficient-funds");
}
```

Spring Boot auto-configures `MockMvcTester` in `@WebMvcTest` tests when AssertJ is on the classpath.

### What to cover per endpoint

- Happy path: status, `Location`/`ETag` headers, key JSON fields and their formats.
- Validation: each important constraint returns `400` with a field error, and the service is not called.
- Business errors: each domain exception maps to the documented `type` and status.
- Security: anonymous (401), wrong scope or role (403), another customer's resource (404).
- Content negotiation: wrong `Content-Type` returns `415`; malformed JSON returns `400`.

## 3. Full application over real HTTP

### `RestTestClient`

Spring Framework 7 adds `RestTestClient`, a fluent test client for servlet applications (the counterpart of `WebTestClient`):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class TransferApiIT {

    @Autowired RestTestClient client;

    @Test
    void transferRoundTrip() {
        String location = client.post().uri("/api/v1/transfers")
                .headers(h -> { h.setBearerAuth(testTokens.customer("c-1")); h.set("Idempotency-Key", "it-1"); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TransferRequest("0123456789", "9876543210", new BigDecimal("2500.00"), "NGN"))
                .exchange()
                .expectStatus().isCreated()
                .returnResult().getResponseHeaders().getLocation().toString();

        client.get().uri(location).headers(h -> h.setBearerAuth(testTokens.customer("c-1")))
              .exchange()
              .expectStatus().isOk()
              .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");
    }
}
```

`RestTestClient.bindTo(mockMvc)` lets the same fluent style run against MockMvc without a server.

### REST Assured

REST Assured is a popular, language-independent-feeling DSL for HTTP tests, often used for black-box suites that QA teams also maintain and run against deployed environments:

```java
@LocalServerPort int port;

@Test
void listsTransactionsWithCursorPagination() {
    given()
        .port(port)
        .auth().oauth2(testTokens.customer("c-1"))
        .queryParam("limit", 2)
    .when()
        .get("/api/v1/accounts/0123456789/transactions")
    .then()
        .statusCode(200)
        .body("data", hasSize(2))
        .body("nextCursor", notNullValue())
        .body("data[0].amount.currency", equalTo("NGN"));
}
```

Point the same suite at staging after each deployment (base URI from an environment variable) as a smoke test.

## 4. Contract testing

Mocks in a consumer's tests encode **assumptions** about the provider. If the provider changes, both test suites stay green and production breaks. **Contract tests** close the gap:

- **Consumer-driven contracts (Pact):** the consumer's tests record the interactions it relies on into a contract file; the provider's build replays them against the real provider and fails if any is broken. A Pact Broker shares contracts between teams.
- **Spring Cloud Contract:** the provider writes contracts (Groovy, YAML or Java); the build generates provider tests and WireMock stubs that consumers download and use in their tests.
- **OpenAPI-based:** validate real responses in your tests against the committed OpenAPI document (for example with a request/response validator library), and diff the document in CI (topic 5.2).

For a junior engineer the practical steps are: keep the OpenAPI file authoritative, validate responses against it in API tests, and use the provider's published stubs (or recorded WireMock mappings) in your consumer tests instead of hand-written ones.

## 5. Making API tests maintainable

- Keep JSON request bodies in text blocks or small builder methods; do not repeat 30-line payloads.
- Assert what matters for the test, not every field (`jsonPath` on key fields, `isLenientlyEqualTo` for shape).
- Generate test tokens in one helper (`testTokens.customer(...)`, `testTokens.admin()`).
- Name tests after the behaviour and the expected status (`returns422WhenFundsAreInsufficient`).

## Common mistakes

- Only happy-path API tests; validation, errors and security untested.
- Full `@SpringBootTest` for every controller test.
- Asserting on whole JSON strings, so any harmless new field breaks tests.
- Testing that the mock returned what the mock was told to return (no real logic exercised).
- Ignoring headers (`Location`, `Content-Type`, `Retry-After`) that clients depend on.
- Hand-written consumer stubs that drift from the real provider.

## Practice

1. Write a `@WebMvcTest` suite for one capstone controller covering the five categories in "What to cover per endpoint".
2. Rewrite two of those tests with `MockMvcTester` and compare readability.
3. Add one `RestTestClient` round-trip test and one REST Assured test against the running application with Testcontainers.
4. Validate your API responses against your committed OpenAPI document in a test, and break a field name to see it fail.

## Key terms

Web slice test, `@WebMvcTest`, `MockMvc`, `MockMvcTester`, `@MockitoBean`, `jsonPath`, `@AutoConfigureMockMvc`, `RANDOM_PORT`, `@LocalServerPort`, `RestTestClient`, `@AutoConfigureRestTestClient`, REST Assured, given–when–then, black-box test, smoke test, contract test, consumer-driven contract, Pact, Spring Cloud Contract, stub, OpenAPI validation.

## Further reading

- [Spring Framework reference: MockMvc and MockMvcTester](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
- [Spring Framework reference: RestTestClient](https://docs.spring.io/spring-framework/reference/testing/resttestclient.html)
- [Spring Boot reference: Testing Spring Boot applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [REST Assured usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage)
- [Pact documentation: Introduction to contract testing](https://docs.pact.io/)
- [Spring Cloud Contract reference](https://docs.spring.io/spring-cloud-contract/reference/)
