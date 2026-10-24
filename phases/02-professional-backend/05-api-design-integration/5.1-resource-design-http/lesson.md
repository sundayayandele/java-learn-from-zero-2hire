## What you will be able to do

- Model an API around **resources** (nouns) and their relationships instead of remote procedure calls.
- Choose the right HTTP method for an operation, and explain **safety** and **idempotency**.
- Return the correct **status code** for success, client errors and server errors, with the right headers (`Location`, `ETag`, `Retry-After`).
- Use **conditional requests** (`ETag`/`If-Match`) to stop lost updates, and caching headers where they help.
- Design endpoints for operations that do not map neatly onto CRUD, such as transfers, reversals and approvals.

## Why this matters in a bank or fintech

Your API is a contract with mobile apps, partner banks, merchants and other internal teams. They write code against it and cannot change at the same pace as you. A badly designed API leaks into every client: a `200 OK` that hides an error makes a mobile app show "Transfer successful" when no money moved; a `POST` that cannot be safely retried turns a network timeout into a double debit. Interviewers for fintech roles almost always ask you to design a small payments or wallet API on a whiteboard, and they listen for exactly these details.

## 1. Resources, not actions

REST (Representational State Transfer) asks you to expose **resources**: things with identity that a client can retrieve and change. URLs name resources; HTTP methods say what to do with them.

| Avoid (RPC style) | Prefer (resource style) |
| --- | --- |
| `POST /getAccount?id=123` | `GET /api/v1/accounts/123` |
| `POST /createCustomer` | `POST /api/v1/customers` |
| `POST /updateCustomerEmail` | `PATCH /api/v1/customers/42` |
| `GET /deleteCard?id=9` | `DELETE /api/v1/cards/9` |
| `POST /listTransactionsForAccount` | `GET /api/v1/accounts/123/transactions?from=2026-10-01` |

Conventions that keep an API predictable:

- **Plural nouns** for collections (`/accounts`), an identifier for one item (`/accounts/{accountNumber}`).
- **Nest only for ownership**, and at most one level: `/accounts/{id}/transactions` reads well; `/customers/{c}/accounts/{a}/transactions/{t}/disputes` does not. Once a sub-resource has its own identity, give it a top-level path too (`/disputes/{id}`).
- **Lower-case, hyphenated** path segments (`/standing-orders`), **camelCase** JSON fields (`accountNumber`), and ISO 8601 for dates and times (`2026-10-24T09:15:00Z`).
- **Money as a decimal string with a currency**, never a floating-point number: `{"amount": "2500.00", "currency": "NGN"}`.
- **Opaque identifiers** for anything sensitive. Expose `"id": "pay_7Hq2..."`, not the database's sequential primary key, which leaks volumes and invites enumeration.

### Operations that are not CRUD

Real banking has operations like "transfer", "reverse", "freeze" and "approve". Two good patterns:

1. **Make the operation a resource.** A transfer is a thing with identity, status and history, so create it: `POST /api/v1/transfers` returns `201 Created` with `Location: /api/v1/transfers/trf_81c...`. A reversal is a new resource too: `POST /api/v1/transfers/trf_81c.../reversals`.
2. **State changes as sub-resources or controlled fields.** Freezing an account can be `POST /api/v1/accounts/123/freeze` (an action sub-resource, acceptable when it is a genuine command with side effects and an audit trail) or `PATCH /api/v1/accounts/123` with `{"status": "FROZEN", "reason": "..."}`. Pick one style per API and use it everywhere.

## 2. HTTP methods, safety and idempotency

| Method | Meaning | Safe? | Idempotent? | Typical success |
| --- | --- | --- | --- | --- |
| `GET` | Read a representation | Yes | Yes | `200 OK` (or `304 Not Modified`) |
| `HEAD` | Like `GET` without a body | Yes | Yes | `200 OK` |
| `POST` | Create a subordinate resource, or run a command | No | **No** | `201 Created` + `Location`, or `202 Accepted`, or `200 OK` |
| `PUT` | Replace the resource at this URL entirely | No | Yes | `200 OK` or `204 No Content` (`201` if it created it) |
| `PATCH` | Partially update | No | Not guaranteed | `200 OK` or `204 No Content` |
| `DELETE` | Remove | No | Yes | `204 No Content` |

- **Safe** means the request does not change server state, so crawlers, prefetchers and retries can call it freely. Never change state on a `GET`.
- **Idempotent** means sending the same request once or ten times leaves the server in the same state. `PUT /accounts/123/nickname` with `"Savings"` is idempotent; `POST /transfers` is not. Clients, proxies and load balancers retry idempotent requests automatically after a network error, which is why the distinction matters so much for money.

Because `POST /transfers` is not idempotent, payment APIs add an **`Idempotency-Key`** header so a client can retry safely. You will implement that in topic 5.3.

## 3. Status codes clients can act on

A status code tells the client **what to do next**, before it reads the body.

| Code | Use it when | Client action |
| --- | --- | --- |
| `200 OK` | Read succeeded, or an update returns the new state | Use the body |
| `201 Created` | A new resource exists; include `Location` | Store the new URL or ID |
| `202 Accepted` | Work queued (for example an NIP transfer awaiting the switch) | Poll the status URL or wait for a webhook |
| `204 No Content` | Success with nothing to return | Nothing |
| `304 Not Modified` | Conditional `GET` and nothing changed | Use the cached copy |
| `400 Bad Request` | Malformed JSON or failed validation | Fix the request; do not retry unchanged |
| `401 Unauthorized` | Missing or invalid credentials | Authenticate (refresh the token) |
| `403 Forbidden` | Authenticated but not allowed | Do not retry; show "not permitted" |
| `404 Not Found` | The resource does not exist (or the caller may not know it exists) | Stop |
| `409 Conflict` | The request conflicts with current state (duplicate reference, account already closed) | Re-read state, then decide |
| `412 Precondition Failed` | `If-Match` ETag no longer matches | Re-read, merge, retry |
| `415 Unsupported Media Type` | Wrong `Content-Type` | Fix the header |
| `422 Unprocessable Content` | Well-formed but breaks a business rule (insufficient funds, limit exceeded) | Show the reason to the user |
| `429 Too Many Requests` | Rate limit hit; send `Retry-After` | Back off |
| `500 Internal Server Error` | Unexpected failure on your side | Retry later, if the operation is idempotent |
| `502`/`503`/`504` | Upstream failure, overload or timeout | Retry with backoff (honour `Retry-After`) |

Two rules that save real money:

1. **Never return `200` with `{"success": false}`.** Monitoring, retries and client error handling all key off the status code.
2. **Return `404` rather than `403` for resources the caller must not even know exist** (another customer's account), so the API does not confirm which account numbers are real.

## 4. Writing it in Spring Boot

```java
@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request,
                                            UriComponentsBuilder uri) {
        Transfer transfer = transfers.start(request);
        URI location = uri.path("/api/v1/transfers/{id}").buildAndExpand(transfer.id()).toUri();
        return transfer.isPending()
                ? ResponseEntity.accepted().location(location).body(TransferResponse.from(transfer))
                : ResponseEntity.created(location).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    ResponseEntity<TransferResponse> get(@PathVariable String id) {
        return transfers.find(id)
                .map(t -> ResponseEntity.ok().eTag(t.version()).body(TransferResponse.from(t)))
                .orElseThrow(() -> new TransferNotFoundException(id));
    }
}
```

- `ResponseEntity.created(location)` sets `201` and the `Location` header in one call.
- A pending transfer returns `202 Accepted` with the same `Location`, so the client polls it or waits for a webhook.
- Domain exceptions (`TransferNotFoundException`, `InsufficientFundsException`) are mapped to status codes once, in a `@RestControllerAdvice` (Phase 1, topic 4.2, and topic 5.3 here).

## 5. Conditional requests stop lost updates

Two support agents open the same customer profile. Agent A changes the phone number, agent B changes the address, and B's save silently overwrites A's change. An **ETag** prevents this.

1. `GET /api/v1/customers/42` returns `ETag: "7"` (the entity's version).
2. The client sends the update with `If-Match: "7"`.
3. If someone else has saved in between (version now `8`), the server returns **`412 Precondition Failed`** instead of overwriting.

```java
@PatchMapping("/{id}")
ResponseEntity<CustomerResponse> patch(@PathVariable long id,
                                       @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                                       @Valid @RequestBody CustomerPatch patch) {
    long expectedVersion = Long.parseLong(ifMatch.replace("\"", ""));
    Customer updated = customers.update(id, expectedVersion, patch);   // throws StaleVersionException
    return ResponseEntity.ok().eTag(String.valueOf(updated.version())).body(CustomerResponse.from(updated));
}
```

Map JPA's `@Version` field to the ETag and translate `ObjectOptimisticLockingFailureException` (or your own `StaleVersionException`) to `412`. For reads, Spring's `ShallowEtagHeaderFilter` can add ETags to any `GET` response and answer `If-None-Match` with `304`, which saves bandwidth for mobile clients even though the server still builds the response.

## 6. Caching headers

- Reference data that rarely changes (bank list, fee schedule, FX rates for a past date) can be cached: `Cache-Control: public, max-age=3600`.
- Anything personal (balances, statements, profile) must not be stored by shared caches: `Cache-Control: no-store` (Spring Security adds sensible defaults when it is on the classpath).
- `Vary: Accept-Language` or `Vary: Authorization` when the representation depends on those headers.

## 7. Collections: filtering, sorting, sparse responses

- Filters as query parameters: `GET /api/v1/accounts/123/transactions?type=DEBIT&from=2026-10-01&to=2026-10-31`.
- Sorting with an allow-list: `?sort=valueDate,desc`. Reject unknown sort fields with `400` (sorting on arbitrary columns invites slow queries and, if concatenated into SQL, injection).
- Always paginate collections that can grow. Topic 5.3 compares offset and cursor pagination.

## Worked example: a wallet API on one page

| Operation | Request | Success |
| --- | --- | --- |
| Open a wallet | `POST /api/v1/wallets` | `201` + `Location: /api/v1/wallets/wal_9f3` |
| View a wallet | `GET /api/v1/wallets/wal_9f3` | `200` + `ETag` |
| Rename | `PATCH /api/v1/wallets/wal_9f3` with `If-Match` | `200`, or `412` if stale |
| Fund by card | `POST /api/v1/wallets/wal_9f3/fundings` + `Idempotency-Key` | `201`, or `202` while the gateway confirms |
| Transfer | `POST /api/v1/transfers` + `Idempotency-Key` | `201`, `202`, `422` (insufficient funds), `409` (duplicate reference) |
| History | `GET /api/v1/wallets/wal_9f3/transactions?cursor=...&limit=50` | `200` with `nextCursor` |
| Freeze | `POST /api/v1/wallets/wal_9f3/freeze` (admin only) | `204`, `409` if already frozen |

## Common mistakes

- Verbs in URLs (`/createTransfer`) and `POST` for everything.
- Changing state on `GET` (a "confirm" link in an email that moves money).
- `200 OK` with an error flag in the body.
- `500` for validation errors or business-rule failures.
- Exposing sequential database IDs or full account numbers where a masked or opaque ID would do.
- Floating-point money in JSON.
- Unbounded collection endpoints with no pagination.
- Ignoring concurrent updates (no ETag or version check).

## Practice

1. Redesign this RPC API as resources and write the method, path and success status for each: `createAccount`, `getBalance`, `doTransfer`, `cancelTransfer`, `listCards`, `blockCard`, `getStatement(month)`.
2. Implement `POST /api/v1/transfers` returning `201` with `Location`, or `202` when the transfer is queued, and a `GET` that returns an `ETag`.
3. Add `If-Match` handling to a `PATCH` endpoint and write a test where two updates race and the second gets `412`.
4. Use `curl -i` to call every endpoint and check the status line and headers, not just the body.

## Key terms

Resource, representation, REST, RPC, collection, sub-resource, safe method, idempotent method, `Location` header, `201 Created`, `202 Accepted`, `204 No Content`, `400` vs `422`, `401` vs `403`, `409 Conflict`, `412 Precondition Failed`, `429 Too Many Requests`, `Retry-After`, ETag, `If-Match`, `If-None-Match`, conditional request, `Cache-Control`, opaque identifier.

## Further reading

- [RFC 9110: HTTP Semantics (methods, status codes, conditional requests)](https://www.rfc-editor.org/rfc/rfc9110)
- [MDN: HTTP response status codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status)
- [Spring Framework reference: Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Framework reference: HTTP caching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html)
- [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md)
- [Google Cloud API design guide: resource-oriented design](https://cloud.google.com/apis/design/resources)
