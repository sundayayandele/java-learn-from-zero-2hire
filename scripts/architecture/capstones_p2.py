"""Architecture of every Phase 2 capstone: profile (drives infra/cloud/technical), logical and solution diagrams, ADRs."""
from __future__ import annotations

from builders import Profile, cloud, infra, technical
from capstones import logical, solution
from model import Architecture, Decision, Diagram, Edge, Group, Node

N, G, E = Node, Group, Edge
PHASE = "02-professional-backend"


def p2(slug: str, title: str, app: str, summary: str, actors: list[tuple[str, str]], **kw) -> Profile:
    return Profile(slug, title, app, summary, actors, idp=True, phase=PHASE, **kw)


def build(p: Profile, logical_d: Diagram, solution_d: Diagram, decisions: list[Decision]) -> Architecture:
    return Architecture(p.slug, p.title, p.summary,
                        [solution_d, logical_d, technical(p), infra(p), cloud(p)], decisions, PHASE)


def secured_monolith(title: str) -> Decision:
    return Decision(
        "One secured Spring Boot service (modular monolith) with an external identity provider",
        f"{title} has closely related capabilities and one team. Authentication is a solved problem that should not be "
        "reimplemented, and the service must work with any OAuth2/OIDC provider.",
        "Build one Spring Boot 4 service, packaged by feature, owning one PostgreSQL database. It is an OAuth2 resource "
        "server: Keycloak (or a managed provider) issues JWTs; the service validates signature, issuer, audience and "
        "expiry locally using the provider's cached public keys, and authorises with scopes, roles and ownership checks.",
        "No passwords or sessions in the service; any compliant identity provider can be swapped in by changing the issuer "
        "URI. Token revocation is only as fast as the token lifetime (keep access tokens short, 5 minutes here).",
        "Session cookies with a login form in the service (couples identity to one app); opaque tokens with "
        "introspection (a network call per request); microservices per capability (Phase 3).")


def contract_first() -> Decision:
    return Decision(
        "Contract first: the API description is written and reviewed before the code",
        "Several clients integrate with this service, and changing an API after they depend on it is expensive.",
        "Write the OpenAPI (or GraphQL schema / proto) contract first, review it in a pull request, then implement. "
        "The running service publishes its contract, and a test compares the published contract with the reviewed one.",
        "Clients can start in parallel against a mock; breaking changes are caught in review. The contract and code "
        "must be kept in step (the test enforces it).",
        "Code first with generated docs only (the API's shape becomes an accident of the implementation).")


# ---------------------------------------------------------------------------------------------- 1. Gateway hub
def gateway() -> Architecture:
    t = "Payment gateway integration hub"
    p = p2("01-payment-gateway-hub", t, "payment-gateway-hub",
           "One internal API in front of several payment providers: initialisation with failover, signed webhooks, "
           "a strict payment state machine, idempotency, refunds and reconciliation.",
           [("merch", "Merchant apps"), ("ops", "Operations")],
           externals=[("pa", "Provider A (Paystack-like)", "HTTPS REST"), ("pb", "Provider B (Flutterwave-like)", "HTTPS REST")],
           job="reconcile pending payments every 5 min",
           controllers=["PaymentController", "WebhookController", "OpsController"],
           services=["PaymentService\n(state machine · idempotency)", "ProviderRouter\n(priority · circuit breakers)",
                     "ReconciliationJob"],
           repositories=["PaymentRepository", "IdempotencyRepository", "WebhookEventRepository"])
    lg = logical(t, "**Logical architecture**: three flows share one payment record. *Initialise* checks the idempotency "
                 "key, picks a provider through the router and stores the payment as `PENDING`. *Webhooks* are verified "
                 "(HMAC over the raw body), de-duplicated by provider event id and applied through the state machine. "
                 "*Reconciliation* verifies stale pending payments. Every transition appends an immutable payment event.",
                 [G("Capabilities", [N("init", "Initialise payment\nidempotency key check", "service"),
                                     N("route", "Provider routing\npriority · circuit breaker", "comp"),
                                     N("hook", "Webhook intake\nsignature · dedup", "service"),
                                     N("sm", "Payment state machine\nlegal transitions only", "comp"),
                                     N("recon", "Reconciliation\nverify stale · abandon", "job"),
                                     N("refund", "Refunds\nnever above amount", "service")]),
                  G("Data (PostgreSQL)", [N("payment", "payment\nreference · merchant · amount · status", "db"),
                                          N("event", "payment_event\nappend-only history", "db"),
                                          N("idem", "idempotency_key\nmerchant · key · request hash · response", "db"),
                                          N("wh", "webhook_event\nprovider · event id (unique)", "db")])],
                 [E("init", "idem"), E("init", "route"), E("init", "payment"), E("hook", "wh", "insert once"),
                  E("hook", "sm"), E("recon", "sm"), E("refund", "sm"), E("sm", "payment"), E("sm", "event")])
    sol = solution(t, "**Solution architecture**: merchant applications integrate once with the hub; the hub integrates "
                   "with each payment provider. Providers call back with signed webhooks. Keycloak issues the merchants' "
                   "tokens; operations watch provider health and payments.",
                   [N("merch", "Merchant apps\n(client credentials)", "actor"), N("cust", "Customers\n(pay on provider page)", "actor"),
                    N("ops", "Operations", "actor")],
                   [N("api", "Payment hub API\n/payments · /webhooks", "service"), N("db", "Payments database", "db")],
                   [N("pa", "Provider A", "ext"), N("pb", "Provider B", "ext")],
                   [E("merch", "api", "HTTPS + JWT"), E("cust", "pa", "card / transfer", True), E("api", "pa", "initialise · verify · refund"),
                    E("api", "pb", "failover"), E("pa", "api", "signed webhook", True), E("api", "db"), E("ops", "api", "status, providers"),
                    E("merch", "kc", "get token")],
                   [N("kc", "Keycloak\n(identity provider)", "infra")])
    dec = [secured_monolith(t), contract_first(),
           Decision("Explicit payment state machine with an append-only event history",
                    "Webhooks arrive late, twice and out of order; verification and reconciliation can race with them.",
                    "Model statuses and legal transitions in one class; every change goes through it inside a transaction "
                    "that locks the payment row and appends a payment_event. Illegal or backward transitions are ignored and logged.",
                    "Out-of-order events can't corrupt state; the history explains every status. More code than a status column.",
                    "Updating the status column directly from each handler."),
           Decision("Idempotency keys for merchants, event-id deduplication for providers",
                    "Merchants retry after timeouts; providers retry webhooks until they get a 2xx.",
                    "Store (merchant, Idempotency-Key, request hash, response) for 24 h and replay the response; store each "
                    "provider event id with a unique constraint and treat a duplicate insert as already processed.",
                    "Exactly-once effects for both directions. Storage for keys (expired by a job).",
                    "Relying on the provider's idempotency alone; in-memory caches (lost on restart, not shared)."),
           Decision("Circuit breaker per provider with priority failover for initialisation only",
                    "A slow or failing provider must not take checkout down, but a payment must never be created twice.",
                    "Each provider call has timeouts and its own Resilience4j circuit breaker. Initialisation fails over to the next "
                    "healthy provider; verify is retried with backoff and jitter; initialise is never retried blindly.",
                    "Checkout survives one provider outage. Some payments go to a costlier provider during outages.",
                    "Retrying everything; one shared breaker for all providers.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 2. Open banking
def open_banking() -> Architecture:
    t = "Secure open banking accounts API"
    p = p2("02-open-banking-api", t, "open-banking-api",
           "An account-information API for third-party providers, gated by explicit, time-limited customer consent "
           "with an audit trail of every access.",
           [("tpp", "Third-party providers (TPPs)"), ("cust", "Customers"), ("ops", "Operations")],
           controllers=["ConsentController (TPP)", "CustomerConsentController", "AccountsController", "AccessLogController"],
           services=["ConsentService\n(lifecycle)", "ConsentGuard\n(permission · account · expiry)", "AccountQueries"],
           repositories=["ConsentRepository", "AccountRepository · TransactionRepository", "AccessLogRepository"])
    lg = logical(t, "**Logical architecture**: the *consent* is the centre. TPPs create it, customers authorise it for "
                 "their own accounts, and every data request passes the *consent guard* (right TPP, authorised, not "
                 "expired, permission granted, account included) before any account data is read. Every decision, allowed "
                 "or denied, is written to the access log.",
                 [G("Capabilities", [N("cons", "Consent management\ncreate · authorise · revoke", "service"),
                                     N("guard", "Consent guard\nTPP · status · expiry · permission · account", "comp"),
                                     N("data", "Account information\naccounts · balances · transactions", "service"),
                                     N("limit", "Unattended access limit\nper account per day", "comp"),
                                     N("audit", "Access log", "service")]),
                  G("Data (PostgreSQL)", [N("consent", "consent\nTPP · customer · permissions · expiry · status", "db"),
                                          N("ca", "consent_account", "db"),
                                          N("acct", "account · transaction\nsynthetic", "db"),
                                          N("log", "access_log\nappend-only", "db")])],
                 [E("cons", "consent"), E("cons", "ca"), E("data", "guard", "every request"), E("guard", "consent"),
                  E("guard", "ca"), E("data", "limit"), E("data", "acct"), E("guard", "audit", "allowed / denied"), E("audit", "log")])
    sol = solution(t, "**Solution architecture**: the bank exposes the API; Keycloak authenticates both the TPPs (client "
                   "credentials) and the customers (authorisation code + PKCE). TPPs never see customer credentials; "
                   "customers review and revoke consents in the bank's own channels.",
                   [N("tpp", "TPP apps\n(budgeting, lending)", "actor"), N("cust", "Bank customers", "actor"),
                    N("ops", "Operations / compliance", "actor")],
                   [N("api", "Open banking API\n/account-access-consents · /accounts", "service"), N("db", "Bank data\nand consents", "db")],
                   [N("cbs", "Core banking\n(source of accounts)", "ext")],
                   [E("tpp", "kc", "client credentials"), E("cust", "kc", "login + approve"), E("tpp", "api", "JWT + x-consent-id"),
                    E("cust", "api", "authorise / revoke"), E("api", "db"), E("ops", "api", "access log"),
                    E("cbs", "db", "accounts, transactions (synthetic here)", True)],
                   [N("kc", "Keycloak\nTPP clients · customers", "infra")])
    dec = [secured_monolith(t), contract_first(),
           Decision("Consent as a first-class resource checked on every data request",
                    "OAuth scopes say what kind of data a TPP may request, not which customer's accounts or until when.",
                    "Store consents with permissions, accounts, expiry and status; a single ConsentGuard evaluates every data "
                    "request against the consent named in x-consent-id and the calling client (azp).",
                    "Fine-grained, revocable, auditable access; one place to test. One extra query per request (indexed).",
                    "Encoding consent details in token scopes (can't be revoked before the token expires; tokens grow)."),
           Decision("Deny with 403 and record every decision in an append-only access log",
                    "Regulators and customers need to know who accessed what and when; probing must not reveal data.",
                    "Write an access_log row for each allowed or denied request in the same transaction as the read; "
                    "responses for accounts outside the consent are 403 without saying whether the account exists.",
                    "Complete audit trail and no existence leaks. Log volume grows with traffic (partition by month later).",
                    "Application logs only (not queryable, easy to lose).")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 3. Vault
def vault() -> Architecture:
    t = "Card tokenisation vault"
    p = p2("03-card-tokenisation-vault", t, "card-vault",
           "A small, isolated vault that swaps card numbers for tokens using envelope encryption, with audited "
           "detokenisation and key rotation.",
           [("checkout", "Checkout service"), ("proc", "Payment processor client")],
           externals=[("kms", "Key management service (KMS / Key Vault)", "HTTPS")],
           job="re-wrap data keys after rotation",
           controllers=["TokenController", "DetokeniseController", "AdminKeyController"],
           services=["Tokeniser\n(Luhn · brand · HMAC lookup)", "EnvelopeCrypto\n(AES-GCM · KEK versions)", "RotationJob"],
           repositories=["CardRepository", "KeyVersionRepository", "AuditRepository"])
    lg = logical(t, "**Logical architecture**: tokenisation validates the card, looks it up by keyed HMAC (same card, same "
                 "token per merchant), encrypts it with a fresh data key, and wraps that key with the current key-encryption "
                 "key (KEK). Detokenisation is a separate, narrowly scoped and audited path. Rotation re-wraps data keys in batches.",
                 [G("Capabilities", [N("tok", "Tokenise\nvalidate · lookup · encrypt", "service"),
                                     N("detok", "Detokenise\nscope + purpose · audited", "service"),
                                     N("crypto", "Envelope encryption\nAES-256-GCM · wrapped data keys", "comp"),
                                     N("rot", "Key rotation\nmaker-checker · batch re-wrap", "job")]),
                  G("Data", [N("card", "card_token\nciphertext · IV · wrapped key · KEK version · HMAC · last4", "db"),
                             N("kek", "KEK versions\n(in KMS; never in the DB)", "store"),
                             N("aud", "detokenise_audit", "db")])],
                 [E("tok", "crypto"), E("detok", "crypto"), E("crypto", "kek", "wrap / unwrap"), E("tok", "card"),
                  E("detok", "card"), E("detok", "aud"), E("rot", "card", "re-wrap"), E("rot", "kek")])
    sol = solution(t, "**Solution architecture**: only the vault and the processor path see card numbers, which keeps "
                   "every other system out of PCI DSS scope. Merchants and checkout work with tokens.",
                   [N("cust", "Customer\n(enters card)", "actor"), N("merch", "Merchants", "actor")],
                   [N("api", "Token vault API\n(isolated network)", "service"), N("db", "Encrypted card store", "db")],
                   [N("checkout", "Checkout service", "ext"), N("proc", "Processor / card network", "ext"), N("kms", "KMS", "ext")],
                   [E("cust", "checkout", "card form"), E("checkout", "api", "tokenise"), E("merch", "checkout", "token only", True),
                    E("proc", "api", "detokenise (scoped)"), E("api", "db"), E("api", "kms", "wrap / unwrap keys")],
                   [N("kc", "Keycloak\nclient scopes", "infra")])
    dec = [secured_monolith(t),
           Decision("Envelope encryption with versioned key-encryption keys",
                    "Card data must be encrypted, keys must rotate, and rotation must not require downtime or a big-bang re-encryption.",
                    "Encrypt each card with its own random AES-256-GCM data key; store the data key wrapped by a KEK held in a KMS, "
                    "with the KEK version. Rotation creates a new KEK; a job re-wraps data keys in batches.",
                    "Fast rotation, crypto-shredding by destroying one data key, KEK never in the database. More moving parts.",
                    "One static key in configuration; database transparent encryption alone (protects disks, not queries)."),
           Decision("Keyed HMAC for card lookup, random tokens",
                    "The same card must map to the same token per merchant, but card numbers are guessable (few billion combinations).",
                    "Look up by HMAC-SHA256(PAN) with a separate secret; tokens are random 128-bit values unrelated to the PAN.",
                    "No brute-forceable hash in the database; tokens reveal nothing. The HMAC secret must be protected like the KEK.",
                    "Plain SHA-256 of the PAN; tokens derived from the PAN."),
           Decision("Separate scope and audit for detokenisation",
                    "Most clients never need the real number; any leak path must be visible.",
                    "Only clients with vault.detokenise may call it, with a stated purpose; every attempt is audited and rate limited.",
                    "Least privilege and traceability. Processor integration needs its own client credentials.",
                    "One scope for all vault operations.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 4. KYC
def kyc() -> Architecture:
    t = "KYC identity verification orchestrator"
    p = p2("04-kyc-verification-orchestrator", t, "kyc-orchestrator",
           "Orchestrates identity, face-match, watchlist and address providers in the right order and in parallel, "
           "decides a KYC tier and records every step.",
           [("app", "Onboarding app"), ("comp", "Compliance analysts")],
           externals=[("idp1", "Identity number provider", "HTTPS"), ("face", "Face match provider", "HTTPS"),
                      ("watch", "Watchlist provider", "HTTPS"), ("addr", "Address provider (async)", "HTTPS + webhook")],
           job="purge raw provider data after 30 days",
           controllers=["VerificationController", "ReviewController", "AddressWebhookController"],
           services=["Orchestrator\n(sequence · parallel checks)", "TierPolicy · NameMatcher", "ProviderClients\n(timeouts · breakers)"],
           repositories=["VerificationRepository", "VerificationEventRepository"])
    lg = logical(t, "**Logical architecture**: the orchestrator runs the cheap, decisive identity check first, then face "
                 "match and watchlist screening in parallel, then waits for the asynchronous address result. The tier "
                 "policy turns results into Tier 1–3 or REFER; compliance decides referrals. Stored data is minimised.",
                 [G("Capabilities", [N("orch", "Orchestration\nsequence · parallel · async", "service"),
                                     N("match", "Name / DOB matching", "comp"), N("tier", "Tier policy\nTier 1–3 · REFER", "comp"),
                                     N("rev", "Manual review", "service"), N("purge", "Data minimisation\nmask · purge", "job")]),
                  G("Data", [N("ver", "verification\nmasked id · HMAC · tier · status", "db"),
                             N("ev", "verification_event\nappend-only", "db")])],
                 [E("orch", "match"), E("orch", "tier"), E("orch", "ver"), E("orch", "ev"), E("rev", "ver"), E("purge", "ev")])
    sol = solution(t, "**Solution architecture**: the onboarding app submits once; the orchestrator calls four external "
                   "providers and returns a tier the account-opening system can act on.",
                   [N("app", "Onboarding app", "actor"), N("comp", "Compliance", "actor")],
                   [N("api", "KYC orchestrator API", "service"), N("db", "Verifications", "db")],
                   [N("idp1", "Identity provider", "ext"), N("face", "Face match", "ext"), N("watch", "Watchlists", "ext"),
                    N("addr", "Address verification", "ext")],
                   [E("app", "api", "HTTPS + JWT"), E("comp", "api", "review REFER"), E("api", "db"), E("api", "idp1"),
                    E("api", "face"), E("api", "watch"), E("api", "addr"), E("addr", "api", "signed webhook", True)],
                   [N("kc", "Keycloak", "infra")])
    dec = [secured_monolith(t),
           Decision("Sequence the decisive check first, then run independent checks in parallel",
                    "Provider calls cost money and time; an identity mismatch makes the other checks pointless.",
                    "Identity lookup first; on a match, face match and watchlist run concurrently on virtual threads; address is async.",
                    "Lower cost and latency. Orchestration code must handle partial results.", "All checks in parallel; all sequential."),
           Decision("Provider outage leads to PENDING_RETRY, not rejection",
                    "Rejecting genuine customers because a provider is down loses business and is unfair.",
                    "Circuit breaker per provider; an open circuit marks the check UNAVAILABLE and the verification PENDING_RETRY.",
                    "Customers aren't penalised for outages. Operations must monitor and retry.", "Fail the verification."),
           Decision("Store masked identifiers plus HMAC, purge raw responses",
                    "Data protection law requires minimisation; identity numbers are sensitive.",
                    "Store only the last four digits and a keyed HMAC for lookups; purge raw provider payloads after 30 days.",
                    "Less to leak. Investigations after 30 days rely on the event summaries.", "Store everything encrypted forever.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 5. Merchant platform
def merchants() -> Architecture:
    t = "Merchant onboarding and API key platform"
    p = p2("05-merchant-api-platform", t, "merchant-platform",
           "Merchant registration with company verification, test and live API keys with hashing, signing, scopes and "
           "rate limits, alongside JWT-secured dashboard endpoints.",
           [("dash", "Merchant dashboard users"), ("srv", "Merchant servers")],
           externals=[("ch", "Companies House API", "HTTPS REST")], redis=True,
           controllers=["MerchantController (JWT)", "KeyController (JWT)", "MerchantApiController (API key)"],
           services=["MerchantService\n(company verification)", "ApiKeyService\n(generate · hash · roll)", "RateLimiter\n(token bucket)"],
           repositories=["MerchantRepository", "ApiKeyRepository", "AuditRepository"])
    lg = logical(t, "**Logical architecture**: two front doors. Dashboard users authenticate with Keycloak JWTs to register "
                 "merchants and manage keys; merchant servers authenticate with API keys (optionally HMAC-signed requests) "
                 "and are rate limited per key. Keys are stored only as hashes.",
                 [G("Capabilities", [N("reg", "Merchant registration\ncompany verification", "service"),
                                     N("keys", "API key lifecycle\ncreate · roll · revoke", "service"),
                                     N("auth", "API key authentication\nhash · constant-time · signature", "comp"),
                                     N("rl", "Rate limiting\nper key", "comp")]),
                  G("Data", [N("m", "merchant\nstatus · company", "db"), N("k", "api_key\nprefix · hash · scopes · mode", "db"),
                             N("a", "key_audit", "db"), N("b", "rate-limit buckets", "store")])],
                 [E("reg", "m"), E("keys", "k"), E("keys", "a"), E("auth", "k"), E("rl", "b")])
    sol = solution(t, "**Solution architecture**: the developer platform of a payments company: businesses sign up, are "
                   "verified against the company registry, and integrate with API keys.",
                   [N("dash", "Merchant staff\n(dashboard)", "actor"), N("srv", "Merchant servers", "actor"), N("ops", "Operations", "actor")],
                   [N("api", "Merchant platform API", "service"), N("db", "Merchants and keys", "db")],
                   [N("ch", "Companies House", "ext")],
                   [E("dash", "api", "JWT"), E("srv", "api", "API key / signature"), E("ops", "api", "verify merchants"),
                    E("api", "ch", "company profile"), E("api", "db")],
                   [N("kc", "Keycloak", "infra"), N("redis", "Redis\nrate limits", "store")])
    dec = [secured_monolith(t),
           Decision("Two security filter chains: JWT for people, API keys for servers",
                    "Dashboard users and merchant servers authenticate differently and reach different endpoints.",
                    "Two SecurityFilterChain beans ordered by path; the API-key chain uses a custom authentication filter.",
                    "Each mechanism stays simple and testable. Two configurations to maintain.", "One chain accepting both everywhere."),
           Decision("Store API key hashes, show the secret once",
                    "A database leak must not expose usable keys.",
                    "Generate 256-bit random secrets, store SHA-256 hashes and an 8-character prefix, compare in constant time.",
                    "Leaked rows are useless. Lost keys can't be recovered, only rolled.",
                    "Encrypting keys (still recoverable by anyone with the key); bcrypt (unnecessary for random secrets)."),
           Decision("Token-bucket rate limits per key in a shared store",
                    "Limits must hold across instances.",
                    "Bucket4j-style buckets in Redis (or PostgreSQL for a single instance) keyed by API key id.",
                    "Fair limits with bursts; standard headers. Redis becomes a dependency.", "Per-instance in-memory counters.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 6. GraphQL
def financials() -> Architecture:
    t = "Company financials GraphQL API"
    p = p2("06-sec-financials-graphql", t, "financials-graphql",
           "A GraphQL API over SEC EDGAR company facts with batched resolvers, query limits and a polite importer.",
           [("apps", "Research and investing apps")],
           externals=[("sec", "SEC EDGAR APIs", "HTTPS JSON")], job="refresh company facts nightly", redis=True,
           controllers=["GraphQL controllers\n(@QueryMapping · @BatchMapping)"],
           services=["FinancialsService\n(metrics · ratios)", "EdgarImporter\n(rate limited)"],
           repositories=["CompanyRepository", "FactRepository (JdbcClient)"])
    lg = logical(t, "**Logical architecture**: an *import* path maps XBRL concepts to metrics and keeps every filed value "
                 "(restatements included); a *query* path resolves GraphQL fields in batches and calculates ratios on read.",
                 [G("Capabilities", [N("imp", "Import\nconcept mapping · restatements", "job"),
                                     N("q", "Query resolution\nbatch loading · limits", "service"),
                                     N("ratio", "Ratios\nBigDecimal · null-safe", "comp")]),
                  G("Data", [N("co", "company\nCIK · ticker · name", "db"), N("f", "fact\nmetric · period · value · filed", "db"),
                             N("c", "query cache", "store")])],
                 [E("imp", "co"), E("imp", "f"), E("q", "co"), E("q", "f", "one query per batch"), E("q", "ratio"), E("q", "c")])
    sol = solution(t, "**Solution architecture**: client apps ask for exactly the fields they need from one GraphQL endpoint; "
                   "the service keeps its own copy of SEC data so clients never hit the SEC directly.",
                   [N("apps", "Client apps", "actor"), N("admin", "Data admins", "actor")],
                   [N("api", "GraphQL API\n/graphql", "service"), N("db", "Financial facts", "db")],
                   [N("sec", "SEC EDGAR", "ext")],
                   [E("apps", "api", "GraphQL + JWT"), E("admin", "api", "importCompany"), E("api", "sec", "User-Agent, ≤10 req/s"), E("api", "db")],
                   [N("kc", "Keycloak", "infra")])
    dec = [secured_monolith(t),
           Decision("Schema-first GraphQL with batch mapping",
                    "Nested queries over many companies cause N+1 database queries.",
                    "Write the schema first; resolve nested fields with @BatchMapping so each level costs one query.",
                    "Predictable performance. Resolvers must be written for batches.", "Per-field resolvers; REST only."),
           Decision("Limit depth, page size and complexity; disable introspection in production",
                    "GraphQL lets clients write expensive queries.",
                    "Instrumentation for max depth 6 and complexity, max page size 100, request timeout; introspection off in prod.",
                    "Protected server. Some legitimate deep queries need redesign.", "Rate limiting alone."),
           Decision("Keep all filed values and select the latest on read",
                    "Companies restate figures; analysts need both the current and the original value.",
                    "Store every (concept, period, filed date) value; queries pick the latest filed per period.",
                    "Correct and auditable. More rows.", "Overwrite on import.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 7. gRPC
def interbank() -> Architecture:
    t = "Interbank transfer gRPC service"
    p = p2("07-interbank-transfer-grpc", t, "interbank-switch",
           "A simplified NIP-style switch and participant banks talking gRPC with deadlines, status queries, idempotent "
           "session ids and mutual TLS.",
           [("mob", "Mobile banking app")],
           externals=[("bank2", "Receiving bank (gRPC, mTLS)", "gRPC / HTTP2")], job="resolve pending transfers",
           controllers=["TransferController (REST edge)", "InterbankGrpcService"],
           services=["TransferService\n(debit · send · reverse)", "SwitchRouter\n(bank code routing)", "StatusResolver"],
           repositories=["AccountRepository", "TransferRepository", "MessageLogRepository"])
    lg = logical(t, "**Logical architecture**: the sending bank runs name enquiry, debits locally, sends the transfer "
                 "through the switch with a deadline, and treats a timeout as *pending*, resolving it by status queries. "
                 "Session ids make every step idempotent; failures reverse the debit.",
                 [G("Sending bank", [N("ne", "Name enquiry\ncached 10 min", "service"), N("tx", "Transfer\nlocal debit · send", "service"),
                                     N("res", "Status resolver\nbackoff · reverse", "job")]),
                  G("Switch", [N("route", "Routing by bank code\nmessage log", "service"), N("set", "Settlement\nnet positions stream", "service")]),
                  G("Receiving bank", [N("cr", "Credit\nidempotent by session id", "service")]),
                  G("Data", [N("db", "accounts · transfers\nper bank", "db"), N("log", "switch message log", "db")])],
                 [E("tx", "ne"), E("tx", "route", "FundsTransfer (deadline 10 s)"), E("route", "cr"), E("res", "route", "TransactionStatus"),
                  E("tx", "db"), E("cr", "db"), E("route", "log"), E("set", "log")])
    sol = solution(t, "**Solution architecture**: customers start transfers in their bank's app; banks exchange messages "
                   "through the switch over gRPC with mutual TLS.",
                   [N("mob", "Customers\n(mobile app)", "actor"), N("ops", "Bank operations", "actor")],
                   [N("bank1", "Sending bank service", "service"), N("sw", "Switch", "service"), N("db", "Databases", "db")],
                   [N("bank2", "Receiving bank", "ext")],
                   [E("mob", "bank1", "REST + JWT"), E("bank1", "sw", "gRPC + mTLS"), E("sw", "bank2", "gRPC + mTLS"),
                    E("bank1", "db"), E("sw", "db"), E("ops", "sw", "settlement report")],
                   [N("kc", "Keycloak\n(customer tokens)", "infra")])
    dec = [Decision("gRPC with Protocol Buffers between banks and switch, REST at the customer edge",
                    "Interbank messages need strict contracts, low latency and deadlines; mobile apps speak JSON.",
                    "Define interbank.proto; services use Spring gRPC. The customer-facing API stays REST/JSON with JWTs.",
                    "Typed contracts, HTTP/2, built-in deadlines and streaming. Needs HTTP/2-capable infrastructure.",
                    "REST between banks; message queues (Phase 3)."),
           Decision("A timeout is PENDING, resolved by status queries",
                    "A transfer can succeed at the receiving bank even when the response is lost.",
                    "Deadline exceeded marks the transfer PENDING; a resolver queries TransactionStatus with backoff; only a "
                    "definite failure reverses the debit.",
                    "No double payments or wrong reversals. Customers sometimes wait for a final status.", "Reverse on timeout."),
           Decision("Mutual TLS between services",
                    "Only member banks may call the switch.",
                    "Each service has a certificate from a private CA; servers require client certificates.",
                    "Strong service identity. Certificate rotation must be automated.", "Shared API keys; network allow-lists only.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 8. Bills
def bills() -> Architecture:
    t = "Bill payments aggregator"
    p = p2("08-bill-payments-aggregator", t, "bill-payments",
           "One bill-payment API over several billers with different protocols, wallet postings, unknown-outcome "
           "resolution, bulkheads and daily reconciliation.",
           [("app", "Banking app customers"), ("ops", "Operations")],
           externals=[("power", "PowerCo (REST, slow)", "HTTPS JSON"), ("telco", "TelcoX (async callbacks)", "HTTPS JSON"),
                      ("cable", "CableTV (XML)", "HTTPS XML")], job="resolve UNKNOWN payments", files=True,
           controllers=["BillPaymentController", "CallbackController", "ReconciliationController"],
           services=["BillPaymentService\n(state machine · wallet)", "BillerAdapters\n(bulkhead · breaker per biller)", "Reconciler"],
           repositories=["PaymentRepository", "WalletPostingRepository", "SettlementRepository"])
    lg = logical(t, "**Logical architecture**: each payment debits the wallet, then vends through the biller's adapter. "
                 "Timeouts become UNKNOWN and are resolved by status queries before any refund. Daily settlement files "
                 "are reconciled against payments.",
                 [G("Capabilities", [N("pay", "Pay bill\nidempotency · validate", "service"), N("wal", "Wallet postings\ndouble entry", "comp"),
                                     N("ad", "Biller adapters\nREST · async · XML", "comp"), N("res", "Unknown resolution", "job"),
                                     N("rec", "Reconciliation", "service")]),
                  G("Data", [N("p", "bill_payment\nstatus · token (encrypted)", "db"), N("w", "wallet · posting", "db"),
                             N("s", "settlement files", "store")])],
                 [E("pay", "wal"), E("pay", "ad"), E("pay", "p"), E("wal", "w"), E("res", "ad", "status query"), E("rec", "s"), E("rec", "p")])
    sol = solution(t, "**Solution architecture**: the banking app integrates once; the aggregator handles each biller's "
                   "protocol, outages and settlement.",
                   [N("cust", "Customers", "actor"), N("ops", "Operations", "actor")],
                   [N("api", "Bill payments API", "service"), N("db", "Payments and wallets", "db")],
                   [N("power", "PowerCo", "ext"), N("telco", "TelcoX", "ext"), N("cable", "CableTV", "ext")],
                   [E("cust", "api", "JWT"), E("ops", "api", "reconcile"), E("api", "power"), E("api", "telco"), E("telco", "api", "callback", True),
                    E("api", "cable", "XML"), E("api", "db")],
                   [N("kc", "Keycloak", "infra")])
    dec = [secured_monolith(t),
           Decision("Adapter per biller with its own bulkhead, timeouts and circuit breaker",
                    "One slow biller must not exhaust resources for the others.",
                    "Each biller adapter has a concurrency limit, timeouts sized to that biller, and a breaker.",
                    "Failures stay isolated. More configuration.", "Shared HTTP client settings for all billers."),
           Decision("UNKNOWN is a status, resolved before refunding",
                    "A vend can succeed even if our call timed out; refunding then gives the customer money and the token.",
                    "Timeouts move to UNKNOWN; a job queries biller status with backoff; only definite failures refund.",
                    "No double value. Some customers wait for resolution.", "Refund on timeout.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 9. Mortgage
def mortgage() -> Architecture:
    t = "Mortgage application decision API"
    p = p2("09-mortgage-decision-api", t, "mortgage-decisions",
           "Explainable, versioned mortgage decision rules with segregation of duties and a backtest against public "
           "HMDA outcomes.",
           [("off", "Loan officers"), ("uw", "Underwriters")],
           externals=[("hmda", "HMDA Data Browser API", "HTTPS CSV")], files=True,
           controllers=["ApplicationController", "RulesetController", "AnalyticsController"],
           services=["DecisionEngine\n(LTV · DTI · reasons)", "RulesetService\n(versions · two-person activation)", "Backtester"],
           repositories=["ApplicationRepository", "DecisionRepository", "HmdaRepository (JdbcClient)"])
    lg = logical(t, "**Logical architecture**: the decision engine reads only financial inputs and the active rule set "
                 "version, and stores inputs, version and reasons with each decision. Overrides and rule changes follow "
                 "segregation-of-duties rules. Protected attributes exist only in the HMDA backtest area.",
                 [G("Decisioning", [N("eng", "Decision engine\nno protected attributes", "service"), N("rs", "Rule sets\nversioned · 2-person activation", "comp"),
                                    N("ov", "Overrides\nnot by submitter", "service")]),
                  G("Analytics", [N("bt", "Backtest and disparity report", "service")]),
                  G("Data", [N("app", "application · decision · override", "db"), N("h", "hmda_lar\n(imported)", "db")])],
                 [E("eng", "rs"), E("eng", "app"), E("ov", "app"), E("bt", "rs"), E("bt", "h")])
    sol = solution(t, "**Solution architecture**: officers submit, the engine decides, underwriters handle referrals and "
                   "overrides, and risk teams backtest rule changes on public data before activating them.",
                   [N("off", "Loan officers", "actor"), N("uw", "Underwriters", "actor"), N("risk", "Risk / admins", "actor")],
                   [N("api", "Decision API", "service"), N("db", "Applications and decisions", "db")],
                   [N("hmda", "HMDA data (FFIEC/CFPB)", "ext")],
                   [E("off", "api", "submit"), E("uw", "api", "override"), E("risk", "api", "rule sets, backtests"),
                    E("api", "hmda", "import CSV"), E("api", "db")],
                   [N("kc", "Keycloak\nroles", "infra")])
    dec = [secured_monolith(t),
           Decision("Versioned rule sets stored with every decision",
                    "Decisions must be reproducible and explainable months later.",
                    "Rule thresholds are data with versions; each decision stores inputs, version and reason codes.",
                    "Any decision can be replayed. Rule changes need a process (two-person activation).", "Rules hard-coded in Java."),
           Decision("Protected attributes are unreachable from the decision path",
                    "Fair lending rules prohibit using race, sex or age in decisions, but fairness must still be measured.",
                    "Decision packages can't import the HMDA/analytics packages (checked by a test); disparity is measured only in backtests.",
                    "Fairness by construction plus measurement. Analysts need separate access.", "Filtering fields at runtime.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 10. Cases
def cases() -> Architecture:
    t = "Fraud case management service"
    p = p2("10-fraud-case-management", t, "fraud-cases",
           "Alert intake, case assignment, investigation, maker-checker actions, SLAs and a tamper-evident audit trail "
           "with object-level authorisation.",
           [("an", "Fraud analysts"), ("sup", "Supervisors"), ("aud", "Auditors")], job="SLA breach check every 5 min",
           controllers=["AlertController", "CaseController", "ActionController", "AuditController"],
           services=["CaseService\n(grouping · assignment)", "ActionService\n(maker-checker)", "CasePolicy\n(object-level access)",
                     "AuditChain\n(hash chain)"],
           repositories=["CaseRepository", "ActionRepository", "AuditRepository"])
    lg = logical(t, "**Logical architecture**: alerts are grouped into cases; analysts work cases they are allowed to "
                 "see (CasePolicy), propose actions that a different supervisor must approve, and every read of sensitive "
                 "data and every change is appended to a hash-chained audit log.",
                 [G("Capabilities", [N("in", "Alert intake\ngroup by customer 48 h", "service"), N("wk", "Case work\nassign · notes · close", "service"),
                                     N("mc", "Maker-checker actions", "service"), N("pol", "CasePolicy\nteam / assignee", "comp"),
                                     N("sla", "SLA monitor", "job"), N("au", "Audit hash chain", "comp")]),
                  G("Data", [N("c", "case · alert · note", "db"), N("a", "action\nproposer · approver", "db"), N("l", "audit_log\nprev_hash · hash", "db")])],
                 [E("in", "c"), E("wk", "pol"), E("mc", "pol"), E("wk", "c"), E("mc", "a"), E("wk", "au"), E("mc", "au"), E("au", "l"), E("sla", "c")])
    sol = solution(t, "**Solution architecture**: fraud rules feed alerts; the fraud team works cases; approved actions "
                   "call card and account systems; auditors read the trail.",
                   [N("an", "Analysts", "actor"), N("sup", "Supervisors", "actor"), N("aud", "Auditors", "actor")],
                   [N("api", "Case management API", "service"), N("db", "Cases and audit", "db")],
                   [N("rules", "Fraud rules engine\n(Phase 1 Capstone 7)", "ext"), N("core", "Card / account systems", "ext")],
                   [E("rules", "api", "alerts (client credentials)"), E("an", "api", "JWT"), E("sup", "api", "approve"),
                    E("aud", "api", "audit"), E("api", "core", "approved actions", True), E("api", "db")],
                   [N("kc", "Keycloak\nroles · teams", "infra")])
    dec = [secured_monolith(t),
           Decision("Object-level authorisation in one policy, enforced with @PreAuthorize",
                    "Role checks alone let any analyst open any case (broken object level authorisation).",
                    "A CasePolicy bean decides access from the case's team and assignee; every endpoint calls it; hidden cases return 404.",
                    "One tested place for access rules. Each new endpoint must use the policy (enforced by an authorisation matrix test).",
                    "Filtering in controllers ad hoc."),
           Decision("Maker-checker for actions that move money or affect customers",
                    "One person must not be able to block a card or refund on their own.",
                    "Actions are proposed and approved by different people with different roles; both identities are stored.",
                    "Fraud-resistant and auditable. Slower for urgent actions (define an emergency path).", "Single-step actions."),
           Decision("Hash-chained audit log",
                    "Auditors must be able to detect altered or deleted audit records.",
                    "Each audit row stores the SHA-256 of its content plus the previous row's hash; a verification endpoint walks the chain.",
                    "Tampering is detectable (not preventable); verification is linear in size.", "Plain audit table.")]
    return build(p, lg, sol, dec)


ALL = [gateway, open_banking, vault, kyc, merchants, financials, interbank, bills, mortgage, cases]
