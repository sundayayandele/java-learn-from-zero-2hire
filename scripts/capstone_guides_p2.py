"""Build steps for the Phase 2 capstone guides (data only; rendered by scripts/capstone_guides.py).

Same shape as CAPSTONES in capstone_guides.py: slug -> (title, repo, package, data commands or None, steps),
where a step is (title, [instructions], done-when).
"""
from __future__ import annotations

SECURITY_TESTS = ("For every endpoint write API tests for: no token (401), wrong scope or role (403), another "
                  "owner's resource (404 or 403 as the brief says), and the happy path. Use `jwt()` from "
                  "`spring-security-test` with the exact authorities each caller has.")

CAPSTONES_P2: dict[str, tuple] = {
    "01-payment-gateway-hub": (
        "Payment gateway integration hub", "payment-gateway-hub", "ng.itlearning.gateway", None, [
            ("Write and review the OpenAPI contract", [
                "Create `src/main/resources/static/openapi.yaml` (or `docs/openapi.yaml`) describing every endpoint in the brief, the `Idempotency-Key` header, the payment schema and every Problem Details type.",
                "Open a pull request containing only the contract and ask a peer (or your mentor) to review it (topic 7.5).",
                "Add a test that fetches `/v3/api-docs` and checks the paths in the contract exist once you implement them.",
            ], "The contract PR is merged and every status code in the brief appears in it."),
            ("Model the payment state machine with TDD", [
                "Write `PaymentStatus` (enum) and a `PaymentStateMachine` with `boolean canMove(from, to)` and `PaymentStatus apply(current, ProviderResult)`.",
                "Write the tests first: every legal transition, every illegal one, and the out-of-order cases (`ongoing` after `SUCCEEDED` keeps `SUCCEEDED`).",
                "Money as `long` minor units plus `Currency`; validate minimum and maximum amounts in a `Money` record.",
            ], "State machine tests cover every arrow in the brief's diagram and all pass."),
            ("Schema and migrations", [
                "`V1__payments.sql`: `payment` (unique `reference`, `merchant_id`, `amount_minor`, `currency`, `status`, `provider`, `version`), `payment_event` (append-only), `idempotency_key` (unique `(merchant_id, idem_key)`), `webhook_event` (unique `(provider, event_id)`), `refund`.",
                "Add `CHECK` constraints for positive amounts and allowed statuses, and a trigger or check that keeps total refunds ≤ amount.",
                "Index `payment(merchant_id, created_at desc, id)` for cursor pagination and `payment(status, updated_at)` for reconciliation.",
            ], "`./mvnw verify` passes and the Flyway history shows your migration on Testcontainers PostgreSQL."),
            ("Provider client with WireMock", [
                "Define `PaymentProvider` (`initialise`, `verify`, `refund`, `supports(currency)`) and implement it once for the brief's provider contract, configured per provider (base URL, secret, priority).",
                "Build the `RestClient` with `ClientHttpRequestFactoryBuilder.jdk()` and `HttpClientSettings.defaults().withConnectTimeout(2s).withReadTimeout(5s)` (topic 5.4).",
                "Test it against WireMock (`wiremock-standalone`): success, 4xx, 5xx, slow responses that exceed the read timeout.",
            ], "Provider client tests pass, including one that proves the read timeout fires."),
            ("Initialise payments with idempotency keys", [
                "`POST /api/v1/payments`: require `Idempotency-Key` (400 if missing); hash the request body (SHA-256 of a canonical JSON) and look up `(merchant, key)`.",
                "Same key + same hash → replay the stored response; same key + different hash → 422 `idempotency-key-reused`.",
                "Insert the key row and the payment in one transaction, call the provider, then store the response. Handle the race where two identical requests arrive together (unique constraint violation → wait and replay).",
            ], "WireMock verifies exactly one provider call when the same request is sent twice, including concurrently."),
            ("Signed webhooks, deduplicated", [
                "`POST /webhooks/{provider}` reads the **raw body** (`@RequestBody byte[]`), computes HMAC-SHA512 with the provider secret and compares with `MessageDigest.isEqual` (constant time).",
                "Permit the webhook path in the security chain without a JWT; the signature is its authentication.",
                "Insert into `webhook_event`; a unique violation means a duplicate: return 200 without reprocessing. Then lock the payment (`SELECT ... FOR UPDATE`) and apply the state machine, appending a `payment_event`.",
            ], "Bad signature → 401 and nothing stored; the same event sent twice concurrently produces one `payment_event`."),
            ("Routing, circuit breakers and retries", [
                "Wrap each provider in its own Resilience4j `CircuitBreaker` (count-based window, 50% failure threshold, 30 s open).",
                "`ProviderRouter` picks the highest-priority provider that supports the currency and whose breaker allows calls; none → 503 `no-provider-available` with `Retry-After`.",
                "Retry `verify` up to 3 attempts with exponential backoff and jitter on timeouts and 5xx (Spring Framework 7 `@Retryable` or Resilience4j `Retry`). Never retry `initialise` blindly.",
                "Expose breaker states at `GET /api/v1/ops/providers` (OPS only).",
            ], "With Provider A stubbed to time out, new payments go to Provider B; with both down the API returns 503."),
            ("Security and ownership", [
                "Scopes: `api.write` to create and refund, `api.read` to read; `ROLE_OPS` may read any payment.",
                "The merchant id is the JWT subject (`CurrentUser.subject()`); queries always filter by it, and another merchant's reference returns 404.",
                SECURITY_TESTS,
            ], "The authorisation tests for every endpoint pass."),
            ("Refunds, reconciliation and metrics", [
                "Refunds: only `SUCCEEDED`/`PARTIALLY_REFUNDED`; lock the payment, check the running total, call the provider, record the refund.",
                "A `@Scheduled` job verifies payments pending > 15 minutes and marks those pending > 24 hours `ABANDONED` (use a `Clock` bean so tests can move time).",
                "Metrics: `payments.initialised{provider,currency}`, `payments.completed{provider,status}`, `webhooks.rejected{reason}`.",
            ], "Every acceptance criterion in the brief has a passing test, and the coverage gate passes."),
        ]),
    "02-open-banking-api": (
        "Secure open banking accounts API", "open-banking-api", "ng.itlearning.openbanking", None, [
            ("Contract and consent model", [
                "Write the OpenAPI contract for the TPP, customer and ops endpoints, with the `x-consent-id` header and every Problem Details type.",
                "Model `Consent` (permissions as an `EnumSet<Permission>`, expiry, transaction window, status) and its lifecycle with TDD: legal and illegal transitions, expiry using an injected `Clock`.",
            ], "Contract reviewed in a PR; consent unit tests cover every transition in the brief."),
            ("Synthetic bank data", [
                "Migrations for `customer`, `account`, `transaction`, `consent`, `consent_account`, `access_log`, `access_counter`.",
                "Write a generator (profile `seed`) creating 200 customers, 2–4 accounts each and 12 months of transactions with realistic Nigerian narrations; customers' `external_subject` matches Keycloak user ids.",
                "Index `transaction(account_id, booked_at, id)` for cursor pagination.",
            ], "The seed runs in under a minute and `EXPLAIN ANALYZE` of a transaction page uses the index."),
            ("Keycloak: TPP client and customers", [
                "Add a confidential client `tpp-budgetapp` (client credentials only) with an `accounts` client scope and the `itl-api` audience mapper to your realm file; its secret comes from the environment, never Git.",
                "Create customer users with `scripts/dev-users.sh` and record their subject ids for the seed.",
                "Get a TPP token with `grant_type=client_credentials` and check the `azp` and `scope` claims at jwt.io-style decoding (locally, never paste real tokens into websites).",
            ], "You can obtain a TPP token and a customer token and the API accepts both."),
            ("Consent endpoints", [
                "TPP endpoints: create (201, `AwaitingAuthorisation`), get, delete (only the creating TPP, identified by the token's `azp`).",
                "Customer endpoints: list, authorise for chosen accounts (all must belong to the customer), reject, revoke.",
                SECURITY_TESTS,
            ], "A customer can't authorise with someone else's account, and a TPP can't see another TPP's consent."),
            ("Consent guard and data endpoints", [
                "Write one `ConsentGuard.check(consentId, tppId, permission, accountId)` that throws a specific exception per failure; unit-test it exhaustively.",
                "Accounts, balances and transactions endpoints call the guard first; transactions are limited to the intersection of the consent and request windows, and `Basic` permissions omit counterparty fields.",
                "Write the `access_log` row for allowed and denied requests (denials in a `REQUIRES_NEW` transaction so they survive the rollback).",
            ], "Each guard failure returns its 403 problem type and leaves a DENIED log row."),
            ("Unattended access limit", [
                "Count calls per (consent, account, endpoint, day) in `access_counter` with an atomic upsert (`INSERT ... ON CONFLICT DO UPDATE ... RETURNING`).",
                "Above the configured limit return 429 with `Retry-After` seconds until midnight Lagos time.",
            ], "The fifth call in a day returns 429 and the counter is correct under 20 concurrent calls."),
            ("Ops access log and hardening", [
                "`GET /ops/v1/access-log` for `ROLE_OPS` with filters and cursor pagination.",
                "Make sure no token, consent body or account number appears in logs; add the consent id as a log correlation field.",
                "Write a one-page threat model (STRIDE) for the consent flow in `architecture/`.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "03-card-tokenisation-vault": (
        "Card tokenisation vault", "card-tokenisation-vault", "ng.itlearning.vault", None, [
            ("Card validation in plain Java", [
                "Implement Luhn, length checks and brand detection from IIN ranges with `@ParameterizedTest` tables, using only test card numbers.",
                "Reject expired cards using an injected `Clock`.",
            ], "Validation tests pass for valid, invalid and boundary cases (expiry this month)."),
            ("Envelope encryption", [
                "`EnvelopeCrypto`: generate a 256-bit data key (`KeyGenerator`), encrypt with `AES/GCM/NoPadding`, 12-byte random IV, 128-bit tag; wrap the data key with the KEK (AES key wrap or GCM) and record the KEK version.",
                "A `KeyEncryptionKeys` interface with a local implementation reading versioned keys from environment variables (base64), never from Git.",
                "Tests: round trip, tampered ciphertext fails, wrong KEK version fails.",
            ], "Crypto tests pass and no key material appears in test output."),
            ("Tokenise and look up", [
                "Schema `card_token` (token, merchant, HMAC, ciphertext, iv, wrapped key, KEK version, last4, brand, expiry, status) with a unique `(merchant_id, pan_hmac)`.",
                "`POST /api/v1/tokens` with `SCOPE_vault.tokenise`: reject any `cvv` field (configure Jackson to fail on unknown properties for this DTO), validate, HMAC lookup, else encrypt and insert.",
            ], "Same card and merchant gives the same token; a SQL search for the PAN digits finds nothing."),
            ("Detokenise with audit", [
                "`POST /api/v1/tokens/{token}/detokenise` with `SCOPE_vault.detokenise` and a `purpose`; write an audit row for every attempt.",
                "Rate-limit detokenisation per client and count it in a metric.",
                SECURITY_TESTS,
            ], "Only the processor client can detokenise; every attempt is audited."),
            ("Key rotation with maker-checker", [
                "`POST /admin/keys/rotate` creates a pending rotation; a different `ADMIN` approves it; then new tokens use the new KEK.",
                "A batch job re-wraps data keys (1,000 rows per transaction, `FOR UPDATE SKIP LOCKED`) and reports progress.",
            ], "After rotation and re-wrap every token still detokenises and no row uses the old version."),
            ("Prove PANs never leak", [
                "Add a logback appender in tests that captures all output; after the suite, assert no test PAN appears.",
                "Mask PANs in `toString()` of every DTO (records override it) and in Problem Details.",
            ], "The leak test passes and the coverage gate is green."),
        ]),
    "04-kyc-verification-orchestrator": (
        "KYC identity verification orchestrator", "kyc-verification-orchestrator", "ng.itlearning.kyc", None, [
            ("Matching and tier rules with TDD", [
                "`NameMatcher`: normalise case, accents, whitespace and order; similarity with a configurable threshold (Jaro-Winkler or token-set ratio).",
                "`TierPolicy`: results → Tier 1/2/3 or `REFER`; watchlist hit always `REFER`.",
            ], "Matching and tier tests pass, including name order swaps and diacritics."),
            ("Provider clients and simulators", [
                "One client per provider with timeouts; WireMock stubs for match, mismatch, slow, 500 and malformed responses.",
                "Synthetic identity generator (CC0) for tests and the seed.",
            ], "Each client has tests for every stub scenario."),
            ("Orchestration", [
                "`POST /api/v1/verifications` returns 202 + `Location`; work runs asynchronously (virtual threads).",
                "Identity first; on match run face match and watchlist in parallel; then wait for the address webhook (signed).",
                "Append a `verification_event` per step; store masked id + HMAC only.",
            ], "Identity mismatch makes no further provider calls; the parallel test shows overlap in timing."),
            ("Resilience and retries", [
                "Circuit breaker per provider; open circuit → check `UNAVAILABLE`, verification `PENDING_RETRY`.",
                "`OPS` retry endpoint and `ADMIN` review endpoint for `REFER`.",
                SECURITY_TESTS,
            ], "Outage and retry acceptance tests pass."),
            ("Data minimisation", [
                "A scheduled purge removes raw provider payloads older than 30 days.",
                "Check logs and Problem Details never include identity numbers.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "05-merchant-api-platform": (
        "Merchant onboarding and API key platform", "merchant-api-platform", "ng.itlearning.merchants", None, [
            ("Keys and signatures in plain Java", [
                "Generate secrets with `SecureRandom` (32 bytes, base62), prefix `sk_test_`/`sk_live_`; hash with SHA-256; compare with `MessageDigest.isEqual`.",
                "Signature: `HMAC-SHA256(secret, t + \".\" + method + \".\" + path + \".\" + body)`; reject timestamps older than 5 minutes.",
            ], "Unit tests cover valid, tampered, expired and replayed requests."),
            ("Two security filter chains", [
                "Chain 1 (`/api/v1/merchants/**`): JWT resource server. Chain 2 (`/api/v1/echo`, `/api/v1/charges-preview`): a custom `ApiKeyAuthenticationFilter` producing an authentication with key scopes as authorities.",
                "Order the chains with `securityMatcher` and `@Order`; test that each rejects the other's credentials.",
            ], "JWTs fail on API-key endpoints and API keys fail on dashboard endpoints."),
            ("Merchant registration and Companies House", [
                "Get a free Companies House API key and keep it in `.env`; the client uses HTTP Basic with the key as username, timeouts and a circuit breaker.",
                "WireMock stubs for active, dissolved and unknown companies; `OPS` verification activates or rejects.",
            ], "Verification tests pass against WireMock; CI never calls the real API."),
            ("Key lifecycle", [
                "Create (secret shown once), list (prefix only), roll (overlap 24 h), revoke; audit every change with the acting user.",
                SECURITY_TESTS,
            ], "No secret is stored in plain text and the roll overlap test passes."),
            ("Rate limiting", [
                "Token bucket per key (Bucket4j with Redis, or a PostgreSQL row with atomic updates for one instance).",
                "Return 429 with `Retry-After` and `RateLimit-Limit`/`RateLimit-Remaining` headers.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "06-sec-financials-graphql": (
        "Company financials GraphQL API", "sec-financials-graphql", "ng.itlearning.financials", [
            "# The SEC requires a descriptive User-Agent with a contact email",
            "UA='YourName yourname@example.com'",
            "curl -A \"$UA\" -o data/company_tickers.json https://www.sec.gov/files/company_tickers.json",
            "curl -A \"$UA\" -o data/CIK0000320193.json https://data.sec.gov/api/xbrl/companyfacts/CIK0000320193.json",
        ], [
            ("Schema first", [
                "Write `src/main/resources/graphql/schema.graphqls` from the brief; add `BigDecimal` and `Date` scalars (graphql-java extended scalars or your own).",
                "Review the schema in a PR before writing resolvers.",
            ], "The schema loads at startup and the PR is merged."),
            ("Importer with recorded data", [
                "Parse a recorded `companyfacts` JSON (Jackson streaming or tree model) and map concepts to metrics from configuration.",
                "Store every (metric, period, filed) value; select the latest filed on read.",
                "The live importer sends the `User-Agent`, waits ≥ 100 ms between requests and is idempotent.",
            ], "Importer tests on the recorded file give the expected annual revenue for three years."),
            ("Resolvers with batching", [
                "`@QueryMapping` for `company`, `companies` (connection with cursors) and `compare`; `@BatchMapping` for `Company.facts` and `Company.ratios`.",
                "Count SQL statements in a test (a datasource proxy or Hibernate statistics) to prove no N+1.",
            ], "The batching test shows a constant number of queries for 20 companies."),
            ("Security and limits", [
                "Secure `/graphql` with `SCOPE_api.read`; `@PreAuthorize(\"hasRole('ADMIN')\")` on `importCompany`.",
                "Add `MaxQueryDepthInstrumentation(6)` and a complexity limit; cap `first` at 100; disable introspection in the `prod` profile.",
                "Test with `HttpGraphQlTester` (or `GraphQlTester`) including rejected deep queries.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "07-interbank-transfer-grpc": (
        "Interbank transfer gRPC service", "interbank-transfer-grpc", "ng.itlearning.interbank", None, [
            ("Proto contract and codes", [
                "Write `src/main/proto/interbank.proto` from the brief and generate Java with the protobuf Maven plugin (see the Spring gRPC getting-started guide).",
                "Implement NUBAN check digits and the response-code enum in plain Java with tests.",
            ], "Generated classes compile and NUBAN tests pass."),
            ("Bank service", [
                "Implement `NameEnquiry` and the credit side of `FundsTransfer` with idempotency by session id.",
                "Test with an in-process gRPC server and a blocking stub.",
            ], "Repeating a session id never credits twice."),
            ("Switch and sending bank", [
                "Switch routes by destination bank code and logs every message; sending bank debits locally then calls the switch with `withDeadlineAfter(10, SECONDS)`.",
                "Deadline exceeded → `PENDING`; a resolver calls `TransactionStatus` with backoff (max 5) then reverses or completes.",
            ], "The slow-receiver test ends with a completed transfer and no reversal."),
            ("mTLS and the REST edge", [
                "Generate a private CA and service certificates with a script (`openssl`), mounted from outside Git; require client certificates on servers.",
                "Add `POST /api/v1/transfers` (JWT, `SCOPE_api.write`, ownership of the source account).",
                SECURITY_TESTS,
            ], "A client without a certificate is refused; REST authorisation tests pass."),
            ("Settlement streaming and Compose", [
                "Server-streaming `StreamSettlementReport` with net positions per bank; test that they sum to zero.",
                "`compose.yaml` runs the switch, two banks, PostgreSQL and Keycloak.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "08-bill-payments-aggregator": (
        "Bill payments aggregator", "bill-payments-aggregator", "ng.itlearning.bills", None, [
            ("State machine and wallet postings", [
                "TDD the status flow including `UNKNOWN`; wallet debits and refunds as balanced postings.",
            ], "State and posting tests pass."),
            ("Three biller adapters", [
                "REST/JSON PowerCo with a long read timeout; TelcoX accepts then calls back (signed); CableTV over XML (Jackson XML).",
                "Stateful WireMock scenarios: timeout-after-vend then status `SUCCESS`; duplicate callbacks.",
            ], "Each adapter's tests pass against its stubs."),
            ("Payment flow with idempotency", [
                "Validate, debit (locked wallet row), vend, record; `Idempotency-Key` required.",
                "Timeouts → `UNKNOWN`; the resolver job queries status before any refund.",
                SECURITY_TESTS,
            ], "No double debit on retry and no refund for a vend that succeeded."),
            ("Bulkheads and breakers", [
                "A concurrency limit per biller (Spring Framework 7 `@ConcurrencyLimit` or a Resilience4j `Bulkhead`) plus a breaker per biller.",
                "Test: PowerCo hangs while TelcoX payments complete normally.",
            ], "The isolation test passes."),
            ("Reconciliation", [
                "Upload a settlement CSV (multipart, size-limited), match by reference, report the three break types.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "09-mortgage-decision-api": (
        "Mortgage application decision API", "mortgage-decision-api", "ng.itlearning.mortgage", [
            "curl -L -o data/hmda-dc-2024.csv \"https://ffiec.cfpb.gov/v2/data-browser-api/view/csv?states=DC&years=2024&actions_taken=1,3\"",
        ], [
            ("Decision engine with TDD", [
                "Monthly payment via the amortisation formula in `BigDecimal` (`MathContext.DECIMAL64`, round to kobo/cents at the end).",
                "LTV and DTI with parameterised boundary tests at exactly 43%, 43.01%, 80%, 95%.",
                "Reason codes and plain-language explanations.",
            ], "Every boundary test passes."),
            ("Versioned rule sets", [
                "Store thresholds as versioned rows; activation needs a second `ADMIN`.",
                "Each decision stores inputs, version and reasons.",
            ], "Replaying an old decision with its version gives the same result."),
            ("Roles and segregation of duties", [
                "Add `OFFICER` and `UNDERWRITER` realm roles; underwriters can't override their own submissions.",
                SECURITY_TESTS,
            ], "Segregation-of-duties tests pass."),
            ("HMDA import and backtest", [
                "Stream-parse the CSV (it's large); map `loan_amount`, `property_value`, `income`, `debt_to_income_ratio` bands and `action_taken`.",
                "Backtest: run a rule set over imported rows and report agreement with actual outcomes, plus approval rates by group with a disparity flag.",
                "An ArchUnit-style test (or a simple package-dependency test) proves the decision package doesn't depend on the HMDA package.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
    "10-fraud-case-management": (
        "Fraud case management service", "fraud-case-management", "ng.itlearning.cases", [
            "# PaySim is on Kaggle (CC BY-SA 4.0): download with the Kaggle CLI after accepting the dataset terms",
            "kaggle datasets download -d ealaxi/paysim1 -p data --unzip",
        ], [
            ("Domain with TDD", [
                "Case and action state machines, SLA calculation from priority, alert grouping (same customer within 48 h).",
                "Hash chain: `hash = SHA-256(prev_hash || canonical record)`; verification walks the chain.",
            ], "Domain and chain tests pass, including detecting a modified record."),
            ("Roles and the case policy", [
                "Add `ANALYST`, `SUPERVISOR`, `AUDITOR` roles and a `team` attribute mapped into the token.",
                "`CasePolicy.canView(auth, caseId)` used from `@PreAuthorize(\"@casePolicy.canView(authentication, #id)\")`; hidden cases return 404.",
            ], "Analysts see only their team's cases."),
            ("Alert intake from PaySim", [
                "A loader turns PaySim fraud rows (or your Phase 1 rules' output) into alerts posted with a client-credentials token.",
            ], "Alerts group into cases as the brief describes."),
            ("Maker-checker, masking and audit", [
                "Propose and approve actions with different people; mask PII in lists; audit full-PII views and every change.",
                "An authorisation matrix test: every role × every endpoint with the expected status.",
                SECURITY_TESTS,
            ], "The matrix test passes."),
            ("SLAs and reports", [
                "Scheduled breach detection with a `Clock`; `GET /api/v1/reports/sla` for `OPS`.",
            ], "All acceptance criteria pass and the coverage gate is green."),
        ]),
}
