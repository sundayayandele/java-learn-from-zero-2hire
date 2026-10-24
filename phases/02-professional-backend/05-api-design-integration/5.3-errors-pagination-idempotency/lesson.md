## What you will be able to do

- Design an **error catalogue** with RFC 9457 Problem Details: stable `type` URIs, titles, extension fields and correlation IDs.
- Implement **offset** and **cursor (keyset)** pagination, and explain when each is appropriate.
- Make `POST` operations safely retryable with an **`Idempotency-Key`**, including concurrent duplicates and payload mismatches.
- Add **rate limiting** responses (`429` with `Retry-After`) that clients can handle.

## Why this matters in a bank or fintech

Errors, pages and retries are where integrations break in production. A partner's code can only handle errors it can recognise, so "Something went wrong" in a different shape from every endpoint becomes a support ticket. A statement endpoint that uses `OFFSET 900000` will time out for a corporate customer with a million transactions. And without idempotency, a mobile network blip during "Send" becomes a double debit and a regulatory complaint. Every major payment API (Stripe, Paystack, Adyen, open-banking standards) solves these three problems in similar ways; this lesson teaches you those ways.

## 1. Problem Details as an error contract

RFC 9457 defines `application/problem+json`:

```json
{
  "type": "https://api.examplebank.ng/problems/insufficient-funds",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Available balance 1,250.00 NGN is less than 2,500.00 NGN.",
  "instance": "/api/v1/transfers",
  "code": "TRF-001",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "availableBalance": "1250.00",
  "currency": "NGN"
}
```

- **`type`** is the stable, documented identifier clients branch on. Never change it; never make clients parse `detail`.
- **`title`** is a short, constant summary for that type. **`detail`** is specific to this occurrence and safe to show users.
- **Extension members** (`code`, `traceId`, `availableBalance`) carry machine-readable context.
- No stack traces, SQL, class names or internal hostnames, ever.

Publish the catalogue in your API docs:

| `type` suffix | Status | When |
| --- | --- | --- |
| `validation-failed` | 400 | Field errors (with an `errors` array) |
| `unauthenticated` | 401 | Missing or invalid token |
| `forbidden` | 403 | Not allowed |
| `not-found` | 404 | Unknown or hidden resource |
| `duplicate-reference` / `idempotency-key-reused` | 409 / 422 | Conflicts |
| `insufficient-funds`, `limit-exceeded`, `account-frozen` | 422 | Business rules |
| `rate-limited` | 429 | Too many requests |
| `upstream-unavailable` | 503 | A dependency (switch, gateway) is down |

### Implementing it in Spring

Spring Boot can render framework errors as Problem Details (`spring.mvc.problemdetails.enabled=true`). For your own exceptions, one advice class keeps the catalogue in one place:

```java
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI BASE = URI.create("https://api.examplebank.ng/problems/");

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds", "Insufficient funds",
                "Available balance is less than the transfer amount.");
        pd.setProperty("code", "TRF-001");
        pd.setProperty("availableBalance", ex.available().toPlainString());
        return pd;
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<ProblemDetail> upstream(UpstreamUnavailableException ex) {
        ProblemDetail pd = problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream-unavailable",
                "Payment provider unavailable", "Please try again shortly.");
        return ResponseEntity.status(503).header(HttpHeaders.RETRY_AFTER, "30").body(pd);
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(BASE.resolve(type));
        pd.setTitle(title);
        pd.setProperty("traceId", MDC.get("traceId"));   // ties the response to your logs
        return pd;
    }
}
```

The `traceId` lets support staff find the exact log lines when a customer quotes it. Phase 3 replaces the MDC value with an OpenTelemetry trace ID.

## 2. Pagination

### Offset pagination

`GET /api/v1/customers?page=3&size=50` maps to `LIMIT 50 OFFSET 150`. Spring Data gives it to you with `Pageable`:

```java
@GetMapping
PageResponse<CustomerSummary> list(@PageableDefault(size = 50, sort = "createdAt", direction = DESC) Pageable pageable) {
    Page<CustomerSummary> page = customers.findAllBy(pageable);
    return PageResponse.from(page);   // content, page, size, totalElements, totalPages
}
```

Good for small or medium tables and admin screens that jump to "page 7". Two problems at scale:

- The database still reads and discards every skipped row, so `OFFSET 900000` is slow.
- If rows are inserted while the user pages, items shift: some appear twice, some never.

Always cap `size` (`spring.data.web.pageable.max-page-size=100`) and use an allow-list for sort properties.

### Cursor (keyset) pagination

For transaction histories, feeds and anything large or constantly growing, page by **position**, not by count:

```sql
SELECT id, value_date, amount, narration
FROM   posting
WHERE  account_id = :account
  AND  (created_at, id) < (:cursorCreatedAt, :cursorId)   -- strictly after the last item seen
ORDER  BY created_at DESC, id DESC
LIMIT  51;                                                -- one extra to know if there is a next page
```

With an index on `(account_id, created_at DESC, id DESC)` every page costs the same, however deep. Return an **opaque cursor** (Base64 of the last `createdAt` and `id`, ideally signed so clients cannot tamper with it):

```json
{
  "data": [ ... 50 items ... ],
  "nextCursor": "eyJ0IjoiMjAyNi0xMC0yNFQwOToxNTowMFoiLCJpZCI6OTg3NjV9",
  "hasMore": true
}
```

The client sends `?cursor=...&limit=50` for the next page. The trade-off: no "jump to page 7" and no cheap total count, which statement and feed screens rarely need.

## 3. Idempotency keys

The client generates a unique key (a UUID) per **logical operation** and sends it with the request:

```
POST /api/v1/transfers
Idempotency-Key: 5f8c1d2e-6a8b-4c0e-9b3f-2d7e1a4c9b60
```

The server's contract:

1. **First request with a key**: process it, store the key with a fingerprint of the request and the response, and return the response.
2. **Same key, same payload**: do not process again; return the **stored** response (same status and body).
3. **Same key, different payload**: reject with `422` (`idempotency-key-reused`); the client has a bug.
4. **Same key while the first request is still running**: return `409 Conflict` (or wait), never process twice.
5. Keys expire after a retention window (for example 24 hours).

A PostgreSQL implementation that is safe under concurrency:

```sql
CREATE TABLE idempotency_key (
    key             VARCHAR(64)  PRIMARY KEY,
    request_hash    CHAR(64)     NOT NULL,           -- SHA-256 of method, path and body
    status          VARCHAR(16)  NOT NULL,           -- IN_PROGRESS, COMPLETED
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

```java
@Transactional
public StoredResponse execute(String key, String requestHash, Supplier<StoredResponse> operation) {
    int inserted = jdbc.sql("""
            INSERT INTO idempotency_key (key, request_hash, status) VALUES (:key, :hash, 'IN_PROGRESS')
            ON CONFLICT (key) DO NOTHING""")
            .param("key", key).param("hash", requestHash).update();

    if (inserted == 0) {                                   // someone has used this key before
        IdempotencyRecord existing = find(key);
        if (!existing.requestHash().equals(requestHash)) throw new IdempotencyKeyReusedException(key);
        if (existing.inProgress()) throw new IdempotentRequestInProgressException(key);
        return existing.response();                        // replay the original result
    }
    StoredResponse response = operation.get();             // the real work, in the same transaction
    complete(key, response);
    return response;
}
```

The primary key turns a race between two identical requests into a clean "one wins, one sees the existing row". Doing the business work and recording the response **in the same database transaction** means a crash can never leave money moved but the key unrecorded (when the work spans systems, such as calling a gateway, record the outcome of each step; Phase 3 covers the outbox pattern).

Idempotency also applies to what you **receive**: payment-gateway webhooks are delivered at least once, so de-duplicate them by event ID (topic 5.4).

## 4. Rate limiting

Protect the API and its downstream systems with per-client limits (for example 100 requests per minute per API key, 5 login attempts per minute per user). When a client exceeds it:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 20
RateLimit-Policy: 100;w=60
Content-Type: application/problem+json
```

Implement it in an API gateway, or in the service with a token bucket (Bucket4j, or Resilience4j's `RateLimiter`), keyed by client ID and stored in Redis when you run several instances.

## Common mistakes

- Every endpoint inventing its own error JSON.
- Clients forced to parse human-readable `detail` text.
- Leaking exception messages (`could not execute statement; SQL [n/a]; constraint [uk_journal_ref]`).
- Unbounded `size` parameters; `OFFSET` pagination on million-row histories.
- Idempotency keys stored in memory (lost on restart, not shared between instances) or checked with a `SELECT` before an `INSERT` (two concurrent requests both pass the check).
- Treating a key reused with a different payload as a replay.
- `429` without `Retry-After`.

## Practice

1. Write an error catalogue for your capstone (at least eight `type`s) and implement it in one `@RestControllerAdvice`, with tests that assert `type`, `status` and `Content-Type: application/problem+json`.
2. Implement cursor pagination for a transaction history with a signed cursor; seed 1 million rows and compare page 1 and page 10,000 timings against offset pagination with `EXPLAIN ANALYZE`.
3. Implement `Idempotency-Key` for `POST /transfers` and test: replay returns the same body; a different payload with the same key returns 422; two concurrent requests with the same key create one transfer.
4. Add a 5-per-minute login rate limit returning `429` with `Retry-After`.

## Key terms

RFC 9457, Problem Details, `application/problem+json`, problem `type`, extension member, error catalogue, trace ID, offset pagination, keyset (cursor) pagination, opaque cursor, `Pageable`, idempotency key, request fingerprint, replay, at-least-once delivery, rate limiting, token bucket, `429 Too Many Requests`, `Retry-After`.

## Further reading

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [Spring Framework reference: Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [IETF draft: The Idempotency-Key HTTP Header Field](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)
- [Stripe API reference: Idempotent requests](https://docs.stripe.com/api/idempotent_requests)
- [Use The Index, Luke!: Paging through results (keyset pagination)](https://use-the-index-luke.com/no-offset)
- [Spring Data Commons reference: Paging and sorting](https://docs.spring.io/spring-data/commons/reference/repositories/query-methods-details.html#repositories.special-parameters)
