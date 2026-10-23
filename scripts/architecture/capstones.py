"""Architecture of every Phase 1 capstone: profile (drives infra/cloud/technical), logical and solution diagrams, ADRs."""
from __future__ import annotations

from builders import Profile, cloud, infra, technical
from model import Architecture, Decision, Diagram, Edge, Group, Node

N, G, E = Node, Group, Edge


def modular_monolith(title: str, extra: str = "") -> Decision:
    return Decision(
        "One deployable Spring Boot service (modular monolith) on PostgreSQL",
        f"{title} has a handful of closely related capabilities, one team (you) and Phase 1 skills. Microservices add "
        "network failure modes, distributed transactions and operational cost without a matching benefit at this size.",
        "Build one Spring Boot 4 service, packaged by feature (one package per capability), with one PostgreSQL "
        "database owned by the service. Keep module boundaries clean so a module could be extracted later." + extra,
        "Simple deployment, local ACID transactions, fast tests with Testcontainers. Scaling is vertical or by adding "
        "stateless instances behind the load balancer; independent scaling of one capability is not possible yet.",
        "Microservices per capability (revisit in Phase 3, Module 10); serverless functions (cold starts and "
        "transaction boundaries are awkward for this workload).")


def solution(title: str, expl: str, actors: list[Node], system: list[Node], externals: list[Node],
             edges: list[Edge], platform: list[Node] | None = None) -> Diagram:
    cols = [G("Users and clients", actors), G(f"{title} (system boundary)", system)]
    if platform:
        cols.append(G("Platform", platform))
    if externals:
        cols.append(G("External systems", externals))
    return Diagram("solution", f"{title}: solution architecture", expl, cols, edges)


def logical(title: str, expl: str, columns: list[Group], edges: list[Edge]) -> Diagram:
    return Diagram("logical", f"{title}: logical architecture", expl, columns, edges)


def build(p: Profile, logical_d: Diagram, solution_d: Diagram, decisions: list[Decision]) -> Architecture:
    return Architecture(p.slug, p.title, p.summary,
                        [solution_d, logical_d, technical(p), infra(p), cloud(p)], decisions)


# ---------------------------------------------------------------------------------------------- 1. Ledger
def ledger() -> Architecture:
    t = "Core banking ledger"
    p = Profile("01-core-banking-ledger", t, "core-banking-ledger",
                "A double-entry ledger service: accounts, deposits, transfers with fees, reversals, statements and a "
                "trial balance, correct under concurrency and safe against retries.",
                [("chan", "Mobile / web banking"), ("ops", "Operations and finance")],
                controllers=["AccountController", "PaymentController", "LedgerController"],
                services=["LedgerService\n(deposit · transfer · reverse)", "FeeSchedule · limits", "LedgerQueries\n(statements · trial balance)"],
                repositories=["AccountRepository\n(PESSIMISTIC_WRITE)", "JournalRepository", "JdbcClient queries"])
    lg = logical(t, "**Logical architecture** shows the capabilities and the data they own, independent of technology. "
                 "Every money movement is a *journal* of balanced *postings*; balances are derived from postings and "
                 "cached on the account in the same transaction. Rules (limits, fees, status) are checked before posting; "
                 "the database enforces the invariants again (balanced journals, immutable postings, unique references).",
                 [G("Capabilities", [N("acct", "Account management\nopen · freeze · status", "service"),
                                     N("pay", "Payments\ndeposit · transfer · reversal", "service"),
                                     N("rules", "Rules\nfees · daily limits · status", "comp"),
                                     N("post", "Posting engine\nbalanced double entry", "service"),
                                     N("rep", "Reporting\nstatements · trial balance · consistency", "service")]),
                  G("Data (PostgreSQL)", [N("account", "account\nnumber · kind · balance · version", "db"),
                                          N("journal", "journal\nunique reference · type · request hash", "db"),
                                          N("posting", "posting\nimmutable · D/C · amount", "db")])],
                 [E("pay", "rules", "check"), E("pay", "post", "post journal"), E("post", "journal"), E("post", "posting"),
                  E("post", "account", "update cached balance"), E("acct", "account"), E("rep", "posting", "window functions"),
                  E("rep", "account")])
    sol = solution(t, "**Solution architecture** places the ledger in its business context: channels call it to move "
                   "money, operations use it for statements and controls, and finance reads the trial balance. In a real "
                   "bank it sits behind an API gateway and feeds the general ledger and reconciliation (Capstone 10).",
                   [N("cust", "Customers\n(via mobile / web)", "actor"), N("ops", "Operations / finance", "actor")],
                   [N("api", "Ledger REST API\n/accounts · /transfers · /ledger", "service"),
                    N("db", "Ledger database\nPostgreSQL", "db")],
                   [N("recon", "Reconciliation\n(Capstone 10)", "ext"), N("fx", "FX rates service\n(Capstone 2, stretch)", "ext")],
                   [E("cust", "api", "HTTPS JSON"), E("ops", "api", "statements, trial balance"), E("api", "db", "ACID transactions"),
                    E("recon", "api", "ledger extract", True), E("api", "fx", "rates for FX journals", True)],
                   [N("obs", "Prometheus / Grafana\nbusiness + JVM metrics", "infra")])
    sol.edges.append(E("obs", "api", "scrape", True))
    dec = [modular_monolith(t),
           Decision("Double-entry journals with immutable postings",
                    "Balances must be explainable to auditors and regulators, and errors must be correctable without losing history.",
                    "Model every money movement as a journal whose postings sum to zero per currency. Never update or delete postings; "
                    "correct with reversing journals. Enforce immutability with a trigger and balance with a deferred constraint trigger.",
                    "Full audit trail and a provable trial balance; corrections are visible. Storage grows with activity (acceptable; archive later).",
                    "Single-entry balance updates with an audit log (easy to drift and hard to prove)."),
           Decision("Pessimistic row locks in a consistent order",
                    "Concurrent transfers from several service instances must not double-spend or deadlock.",
                    "Lock the customer accounts involved with SELECT ... FOR UPDATE in id order in one query; lock internal accounts last.",
                    "Correct across any number of instances; no deadlocks. The fee-income account is a hot row at high volume "
                    "(mitigate later by sharding fee accounts or posting fees asynchronously).",
                    "Optimistic locking with retries (many retries under contention); in-memory locks (don't work across instances)."),
           Decision("Idempotency by client reference plus request fingerprint",
                    "Mobile clients retry after timeouts; a retry must never post twice.",
                    "Require a reference per request; store it uniquely with a SHA-256 hash of the payload. Same reference and "
                    "payload returns the original receipt (200); different payload returns 409.",
                    "Safe retries; clear client contract. Clients must generate unique references.",
                    "Idempotency-Key header stored in Redis (extra moving part for no gain here)."),
           Decision("JPA for writes, SQL (JdbcClient) for reads",
                    "Writes benefit from entity behaviour and locking; statements and trial balances need window functions and aggregates.",
                    "Use Spring Data JPA entities for accounts and journals, and JdbcClient with hand-written SQL for read models.",
                    "Each side uses the best tool; two styles to learn. ddl-auto=validate keeps entities honest against Flyway.",
                    "JPA everywhere (N+1 and awkward aggregates); SQL everywhere (more boilerplate for writes).")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 2. FX rates
def fx() -> Architecture:
    t = "FX reference rates service"
    p = Profile("02-fx-rates-service", t, "fx-rates-service",
                "Imports ECB euro reference rates daily, keeps the full history, and serves rates, cross rates, "
                "conversions and history with business-day fallback and attribution.",
                [("apps", "Apps, finance, treasury")],
                externals=[("ecb", "European Central Bank\neurofxref-hist.zip", "HTTPS")],
                job="weekdays 16:45 Europe/Berlin", controllers=["RatesController", "AdminController"],
                services=["EcbDownloader · RateImporter\n(advisory lock)", "RateService\n(cross rates · conversion)"],
                repositories=["JDBC batch insert\nON CONFLICT DO NOTHING", "RateRepository\n(@Cacheable)"])
    lg = logical(t, "**Logical architecture**: an *ingestion* path (download → parse → idempotent load → cache eviction) "
                 "and a *query* path (validate → resolve publication date → cross rate → round once). The store holds "
                 "exactly what the ECB publishes (units per EUR); everything else is derived on read and labelled as such.",
                 [G("Ingestion", [N("dl", "Download\n(timeouts)", "service"), N("parse", "Parse CSV\nN/A cells · line numbers", "comp"),
                                  N("load", "Idempotent load\nbatched upsert", "service"), N("log", "Import run log", "comp")]),
                  G("Query", [N("val", "Validate codes and dates", "comp"), N("res", "Resolve publication date\nbusiness-day fallback", "service"),
                              N("calc", "Cross rate / conversion\n10 dp HALF_EVEN, round once", "service")]),
                  G("Data", [N("rates", "fx_rate\n(currency, date) → per EUR", "db"), N("runs", "import_run", "db"),
                             N("cache", "Rate cache\n(Caffeine)", "store")])],
                 [E("dl", "parse"), E("parse", "load"), E("load", "rates"), E("load", "log"), E("log", "runs"),
                  E("load", "cache", "evict", True), E("val", "res"), E("res", "calc"), E("calc", "cache", "lookup"),
                  E("cache", "rates", "miss", True)])
    sol = solution(t, "**Solution architecture**: the service is the bank's single source of *reference* rates for "
                   "reporting, valuation and display. The ECB is the upstream source; the US Treasury dataset is a "
                   "stretch source for currencies the ECB doesn't publish (such as NGN). Dealing rates for customer "
                   "trades are a separate concern.",
                   [N("apps", "Mobile app / web\n(display rates)", "actor"), N("fin", "Finance & treasury\n(valuation, reporting)", "actor"),
                    N("ledger", "Ledger (Capstone 1)\nFX journals", "actor")],
                   [N("api", "FX rates REST API\n/rates · /conversions", "service"), N("store", "Rates DB + cache", "db")],
                   [N("ecb", "ECB reference rates\n(ESCB reuse policy)", "ext"), N("ust", "US Treasury rates\n(stretch)", "ext")],
                   [E("apps", "api", "HTTPS"), E("fin", "api", "HTTPS"), E("ledger", "api", "HTTPS", True), E("api", "store"),
                    E("api", "ecb", "daily import"), E("api", "ust", "quarterly import", True)],
                   [N("sch", "Scheduler\n(@Scheduled or cloud scheduler)", "job")])
    sol.edges.append(E("sch", "api", "trigger import", True))
    dec = [modular_monolith(t),
           Decision("Store ECB values per EUR; derive inverse and cross rates on read",
                    "The ECB's reuse policy forbids modifying its statistics; consumers need any pair.",
                    "Persist exactly the published per-EUR values; compute rate(base→quote) = perEur(quote) / perEur(base) at 10 dp HALF_EVEN; flag derived values.",
                    "Faithful to the source and auditable; a little computation per request (cached).",
                    "Pre-computing all pairs (n² rows, harder to keep consistent)."),
           Decision("Round once, to the target currency's minor units",
                    "Converting through an intermediate rounded rate causes double rounding.",
                    "Compute amount × perEur(to) ÷ perEur(from) in one step and round to Currency.getDefaultFractionDigits() with HALF_EVEN.",
                    "Exact, explainable results (JPY has 0 decimals, USD 2).", "Rounding the rate first, then the amount."),
           Decision("Idempotent import guarded by a PostgreSQL advisory lock",
                    "Imports can be retried and several instances may run the scheduler.",
                    "Insert with ON CONFLICT DO NOTHING; take pg_try_advisory_xact_lock so only one import runs; log runs in REQUIRES_NEW transactions.",
                    "Re-runs are harmless; failures are recorded even when the import rolls back.", "ShedLock or Quartz clustering (more dependencies)."),
           Decision("Platform scheduler on scale-to-zero platforms",
                    "@Scheduled doesn't fire while Cloud Run / Container Apps instances are scaled to zero.",
                    "Keep @Scheduled for VPS and always-on containers (configurable); use EventBridge Scheduler, Cloud Scheduler or Container Apps jobs to call the admin endpoint elsewhere.",
                    "Works on every target; one more piece of cloud configuration.", "Min instances = 1 everywhere (costs more).")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 3. Loans
def loans() -> Architecture:
    t = "Loan amortisation and repayment service"
    p = Profile("03-loan-amortisation-service", t, "loan-amortisation-service",
                "Quotes and books loans with exact repayment schedules (reducing balance, flat, interest-only), "
                "allocates repayments, tracks arrears and penalties, and computes early payoff.",
                [("officer", "Loan officers"), ("cust", "Customers (app)")],
                externals=[("wb", "World Bank WDI API\nlending rates", "HTTPS")], job="nightly arrears and penalties",
                controllers=["ProductController", "QuoteController", "LoanController", "PortfolioController"],
                services=["ScheduleEngine\n(sealed RepaymentMethod)", "LoanService\n(booking · repayments)", "ArrearsService\n(penalties · buckets)"],
                repositories=["LoanProductRepository", "LoanRepository · InstalmentRepository", "Portfolio queries (SQL)"])
    lg = logical(t, "**Logical architecture**: a pure *schedule engine* (no I/O, exhaustively tested) used by quoting "
                 "and booking; *servicing* allocates repayments in a configurable order; a nightly *arrears* job ages "
                 "loans and posts penalties; *portfolio* reporting buckets loans by days past due.",
                 [G("Origination", [N("prod", "Product catalogue\nmethod · rate/tenor ranges", "comp"),
                                    N("eng", "Schedule engine\nreducing · flat · interest-only", "service"),
                                    N("quote", "Quote\nschedule · EAR · total cost", "service"), N("book", "Book loan", "service")]),
                  G("Servicing", [N("repay", "Repayment allocation\npenalty → interest → principal", "service"),
                                  N("arrears", "Arrears and penalties\nnightly", "job"), N("payoff", "Early payoff", "service")]),
                  G("Data", [N("loan", "loan · instalment", "db"), N("repaydb", "repayment\n(unique reference)", "db"),
                             N("pr", "loan_product", "db")])],
                 [E("prod", "eng"), E("quote", "eng"), E("book", "eng"), E("book", "loan"), E("repay", "loan"), E("repay", "repaydb"),
                  E("arrears", "loan", "age, penalise"), E("payoff", "loan"), E("prod", "pr")])
    sol = solution(t, "**Solution architecture**: officers and the customer app quote and book loans; collections works "
                   "from the arrears portfolio; disbursements and repayments would post to the core ledger (Capstone 1). "
                   "Market lending rates from the World Bank inform product configuration.",
                   [N("officer", "Loan officers", "actor"), N("cust", "Customers (app)", "actor"), N("coll", "Collections team", "actor")],
                   [N("api", "Loan servicing API", "service"), N("db", "Loans DB", "db"), N("job", "Nightly arrears job", "job")],
                   [N("ledger", "Core ledger (Capstone 1)\ndisbursement / repayment postings", "ext"), N("wb", "World Bank WDI\n(CC BY 4.0)", "ext")],
                   [E("officer", "api", "quote, book"), E("cust", "api", "schedule, repay"), E("coll", "api", "arrears buckets"),
                    E("api", "db"), E("job", "db", "age loans"), E("api", "ledger", "post (stretch)", True), E("api", "wb", "reference rates", True)])
    dec = [modular_monolith(t),
           Decision("Sealed RepaymentMethod with a pure schedule engine",
                    "Several amortisation methods; adding one must not break the others.",
                    "Model methods as a sealed interface of records and compute schedules in a pure function with exhaustive switches.",
                    "Compiler-checked completeness; parameterised tests over 1,000 real cases.", "Strategy beans looked up by name (no exhaustiveness)."),
           Decision("Final instalment absorbs rounding",
                    "Rounding each instalment to kobo leaves a residue.",
                    "Round every instalment HALF_EVEN and make the last instalment pay the exact remaining principal.",
                    "Principal repaid always equals principal lent.", "Spreading the residue (harder to explain to customers)."),
           Decision("Injected Clock and business-date arithmetic",
                    "Arrears, penalties and payoff depend on today's date.",
                    "Inject java.time.Clock; compute business dates in Africa/Lagos; test with fixed clocks.",
                    "Deterministic, time-travel tests.", "LocalDate.now() calls (untestable)."),
           Decision("Nightly arrears job with a single-runner lock",
                    "Ageing and penalties must run once per day even with several instances.",
                    "Run the job via @Scheduled (or a cloud scheduler) and guard it with a PostgreSQL advisory lock; make it idempotent per business date.",
                    "Safe re-runs and multi-instance deployment.", "Running penalties on read (inconsistent reports).")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 4. Scorecard
def scorecard() -> Architecture:
    t = "Credit-card default risk scorecard API"
    p = Profile("04-credit-risk-scorecard-api", t, "credit-risk-scorecard-api",
                "Scores 30,000 card holders with a versioned, explainable rule-based scorecard, runs bulk re-scoring "
                "concurrently, and measures how well grades separate defaulters.",
                [("risk", "Credit risk team"), ("cards", "Card operations")],
                job="bulk scoring runs (on demand)", files=True,
                controllers=["ScoreController", "ScoringRunController", "RuleController", "ImportController"],
                services=["ScoringEngine\n(pure, sealed ScoreRule)", "ScoringRunService\n(virtual threads, batches)", "RuleService\n(versioned, audited)"],
                repositories=["CustomerRepository", "ScoreRepository (JDBC batch)", "RuleSetRepository"])
    lg = logical(t, "**Logical architecture**: rules are *data*, versioned and audited; the *scoring engine* is a pure "
                 "function of (profile, rule-set version) that returns a score, grade, action and reason codes; *runs* "
                 "re-score the whole book concurrently; *performance analytics* compare grades with actual defaults.",
                 [G("Inputs", [N("imp", "Dataset import\n(xls → CSV, idempotent)", "service"), N("rules", "Rule management\nversioned · validated · audited", "service")]),
                  G("Scoring", [N("eng", "Scoring engine\npure function", "service"), N("run", "Scoring run\nvirtual threads · batches", "job"),
                                N("explain", "Reason codes\n(explainability)", "comp")]),
                  G("Analytics", [N("perf", "Performance\ndefault rate per grade · Gini/KS", "service")]),
                  G("Data", [N("cust", "customer_profile", "db"), N("rs", "rule_set · rule\n(versioned)", "db"), N("score", "score\n(rule_set_version)", "db")])],
                 [E("imp", "cust"), E("rules", "rs"), E("run", "eng"), E("eng", "explain"), E("eng", "rs", "read version"),
                  E("run", "cust"), E("run", "score", "batch insert"), E("perf", "score"), E("perf", "cust", "default label")])
    sol = solution(t, "**Solution architecture**: the risk team owns the rules and reviews performance; card operations "
                   "act on grades (limit changes, reviews). Scores feed limit-management systems; protected attributes are "
                   "excluded by design.",
                   [N("risk", "Credit risk team", "actor"), N("cards", "Card operations", "actor")],
                   [N("api", "Scorecard API", "service"), N("db", "Scores and rules DB", "db")],
                   [N("uci", "UCI Default of Credit Card Clients\n(CC BY 4.0)", "ext"), N("lim", "Limit management\n(consumer system)", "ext")],
                   [E("risk", "api", "rules, runs, performance"), E("cards", "api", "scores, reasons"), E("api", "db"),
                    E("uci", "api", "one-off import"), E("api", "lim", "grade → action", True)])
    dec = [modular_monolith(t),
           Decision("Rules as versioned data, scores record their version",
                    "Risk teams change rules without deployments, and past decisions must stay explainable.",
                    "Store rules in PostgreSQL with a rule-set version; every score stores the version used; changes are validated and audited.",
                    "Explainable history; rule changes are a governed data change.", "Rules in code (deployment per change)."),
           Decision("Pure scoring function",
                    "Scoring must be fast, deterministic and testable at boundaries.",
                    "The engine takes a profile and a rule set and returns score, grade, action and reason codes; no I/O inside.",
                    "Trivial unit tests; safe to run in parallel.", "Scoring inside SQL (hard to explain and test)."),
           Decision("Bulk runs on virtual threads with batched writes",
                    "Re-scoring 30,000 customers should take seconds and not overload the database.",
                    "Partition customers into chunks processed on virtual threads; write scores with JDBC batches; bound concurrency with a semaphore.",
                    "Fast and resource-bounded; progress is observable.", "Single-threaded loop (slow); parallel streams (shared pool)."),
           Decision("Exclude protected attributes",
                    "The dataset includes sex and marital status; using them is discriminatory and often illegal.",
                    "Never use sex or marital status as inputs; report outcomes by grade with and without age as a fairness check.",
                    "Defensible model; slightly lower raw separation is possible.", "Using all columns 'because they predict'.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 5. Complaints
def complaints() -> Architecture:
    t = "Consumer complaints triage and SLA service"
    p = Profile("05-complaints-triage-service", t, "complaints-triage-service",
                "Ingests millions of real CFPB complaints, routes them to teams, tracks SLA deadlines on business days, "
                "runs a status workflow and reports breaches.",
                [("cc", "Contact centre agents"), ("comp", "Compliance")],
                externals=[("cfpb", "CFPB complaint database\n(bulk CSV / API)", "HTTPS")], job="SLA breach evaluation",
                mongo=True, files=True,
                controllers=["ComplaintController", "WorkflowController", "ReportController", "ImportController"],
                services=["IngestionService\n(streaming CSV, COPY/batch)", "TriageService\n(routing rules · SLA clock)", "WorkflowService\n(state machine, audit)"],
                repositories=["ComplaintRepository + FTS queries", "BusinessCalendarRepository", "Narratives (MongoDB)"])
    lg = logical(t, "**Logical architecture**: *ingestion* streams the bulk file into staging and upserts complaints; "
                 "*triage* applies data-driven routing rules and starts an SLA clock on business days; the *workflow* "
                 "enforces legal status transitions with an audit trail; *search and reporting* use full-text indexes and "
                 "SQL aggregates. Narratives may live in MongoDB; structured fields stay relational.",
                 [G("Intake", [N("ing", "Bulk ingestion\nstreaming CSV, idempotent", "service"), N("new", "New complaint API\n(bank channels)", "service")]),
                  G("Triage and SLA", [N("route", "Routing rules\nproduct + issue + keywords", "service"),
                                       N("sla", "SLA clock\nbusiness days + holidays", "service"), N("wf", "Workflow\nstate machine + audit", "service")]),
                  G("Insight", [N("search", "Search\nfull-text (GIN)", "comp"), N("rep", "Reports\nbreaches · monthly trends", "comp")]),
                  G("Data", [N("cdb", "complaint · company · issue\n(PostgreSQL)", "db"), N("cal", "business_calendar", "db"),
                             N("narr", "narratives\n(MongoDB or JSONB)", "store")])],
                 [E("ing", "cdb"), E("ing", "narr"), E("new", "route"), E("route", "sla"), E("sla", "cal"), E("wf", "cdb", "transitions"),
                  E("search", "cdb"), E("search", "narr", "", True), E("rep", "cdb")])
    sol = solution(t, "**Solution architecture**: complaints arrive from bank channels and from the public CFPB dataset "
                   "(standing in for a regulator feed); agents work queues per team; compliance monitors SLA breaches and "
                   "regulatory reporting.",
                   [N("cust", "Customers\n(web, branch, contact centre)", "actor"), N("agents", "Agents (per team)", "actor"),
                    N("compl", "Compliance / management", "actor")],
                   [N("api", "Complaints API", "service"), N("pg", "PostgreSQL", "db"), N("mg", "MongoDB (optional)", "store")],
                   [N("cfpb", "CFPB complaint database\n(US public domain)", "ext"), N("notify", "Email / SMS provider\n(stretch)", "ext")],
                   [E("cust", "api", "new complaint"), E("agents", "api", "queues, transitions"), E("compl", "api", "SLA reports"),
                    E("api", "pg"), E("api", "mg", "narratives", True), E("cfpb", "api", "bulk import"), E("api", "notify", "SLA alerts", True)])
    dec = [modular_monolith(t),
           Decision("PostgreSQL full-text search first; MongoDB optional for narratives",
                    "Narratives are long free text; most queries combine text with structured filters.",
                    "Use tsvector + GIN in PostgreSQL by default; allow MongoDB for narratives as a documented, measured choice.",
                    "One store for most learners; comparison is an explicit learning goal.", "Elasticsearch (another cluster to run)."),
           Decision("Business-day SLA clock from a calendar table",
                    "SLA deadlines skip weekends and public holidays, which change yearly.",
                    "Keep holidays as data in business_calendar; compute deadlines with an injected Clock.",
                    "Correct deadlines; operations can add holidays without code.", "Hard-coded holiday lists."),
           Decision("Explicit workflow state machine with audit",
                    "Illegal status changes and missing history are regulatory findings.",
                    "Model statuses as an enum with an allowed-transition table; reject illegal transitions (409) and append every change to an audit table.",
                    "Predictable, auditable lifecycle.", "Free-form status updates."),
           Decision("Streaming, idempotent bulk import",
                    "The source file is several GB with quoted multi-line fields.",
                    "Stream with a real CSV parser, filter while reading, load in batches/COPY, upsert on complaint ID.",
                    "Constant memory, repeatable imports.", "Loading the whole file into memory; split(',') parsing.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 6. Screening
def screening() -> Architecture:
    t = "Sanctions screening service"
    p = Profile("06-sanctions-screening-service", t, "sanctions-screening-service",
                "Loads OFAC and UK sanctions lists as immutable versions, screens names with normalisation and fuzzy "
                "matching, and supports an auditable analyst workflow.",
                [("onb", "Onboarding / payments"), ("analyst", "Compliance analysts")],
                externals=[("ofac", "OFAC Sanctions List Service", "HTTPS"), ("uk", "UK Sanctions List (FCDO)", "HTTPS")],
                job="daily list refresh", redis=True,
                controllers=["ScreeningController", "AlertController", "ListAdminController"],
                services=["ListImportService\n(versioned lists)", "MatchingService\n(normalise · trigram · Jaro-Winkler)", "AlertService\n(decisions · allow-list)"],
                repositories=["ListEntryRepository\n(pg_trgm GIN)", "ScreeningRepository", "AlertRepository"])
    lg = logical(t, "**Logical architecture**: *list management* imports each source into an immutable list version with "
                 "normalised tokens; *matching* generates candidates cheaply in the database (trigram index) and scores "
                 "them precisely in Java; *decisioning* applies thresholds, allow-lists and records the evidence; "
                 "*case work* lets analysts confirm or dismiss alerts with mandatory reasons.",
                 [G("List management", [N("imp", "Import + normalise\nNFKD · tokens · suffixes", "service"), N("ver", "List versions\n(immutable)", "comp")]),
                  G("Matching", [N("cand", "Candidate generation\npg_trgm index", "service"), N("score", "Scoring\nJaro-Winkler + token set", "service"),
                                 N("dec", "Decision\nthresholds · DOB · country", "service")]),
                  G("Case work", [N("alert", "Alerts", "comp"), N("allow", "Allow-list\n(per entry version)", "comp")]),
                  G("Data", [N("entries", "list_entry · alias\n(normalised)", "db"), N("scr", "screening\n(evidence, versions)", "db"),
                             N("cache", "Result cache\n(Redis)", "store")])],
                 [E("imp", "ver"), E("ver", "entries"), E("cand", "entries"), E("cand", "score"), E("score", "dec"), E("dec", "scr"),
                  E("dec", "alert", "potential match"), E("allow", "dec", "suppress", True), E("dec", "cache", "", True)])
    sol = solution(t, "**Solution architecture**: onboarding and the payments engine call the service synchronously before "
                   "opening an account or releasing a payment; potential matches create alerts that compliance analysts "
                   "resolve. Lists refresh daily from official publishers.",
                   [N("onb", "Customer onboarding", "actor"), N("pay", "Payments engine\n(pre-release check)", "actor"),
                    N("an", "Compliance analysts", "actor")],
                   [N("api", "Screening API", "service"), N("db", "Lists, screenings, alerts", "db"), N("rd", "Redis cache", "store")],
                   [N("ofac", "OFAC SDN + consolidated\n(US public domain)", "ext"), N("uk", "UK Sanctions List\n(OGL v3)", "ext")],
                   [E("onb", "api", "screen name"), E("pay", "api", "screen beneficiary"), E("an", "api", "decide alerts"),
                    E("api", "db"), E("api", "rd"), E("ofac", "api", "daily import"), E("uk", "api", "daily import")])
    dec = [modular_monolith(t),
           Decision("Candidate generation in PostgreSQL, scoring in Java",
                    "Scoring every list entry per request is too slow; SQL alone can't express the full scoring logic.",
                    "Use a pg_trgm GIN index to fetch a small candidate set, then score with Jaro-Winkler and token-set similarity in Java.",
                    "Millisecond screening with explainable scores.", "Brute-force scoring; a search engine cluster."),
           Decision("Immutable list versions recorded on every decision",
                    "Auditors ask which list a decision was based on, months later.",
                    "Each import creates a version (checksum, timestamp); screenings store the versions used; unchanged files create no version.",
                    "Full traceability.", "Overwriting list tables in place."),
           Decision("Configurable thresholds owned by compliance",
                    "False positives cost analyst time; false negatives are regulatory breaches.",
                    "Externalise high/low thresholds via validated configuration; changes are reviewed like code.",
                    "Tunable without code changes; documented ownership.", "Hard-coded thresholds."),
           Decision("Cache results keyed by normalised input and list version",
                    "Repeated screenings of the same names are common.",
                    "Cache in Redis with the list version in the key so a new version naturally invalidates entries.",
                    "Fast repeats, no stale decisions across list updates.", "Time-based expiry only (stale after updates).")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 7. Fraud rules
def fraud() -> Architecture:
    t = "Mobile money fraud rules engine"
    p = Profile("07-mobile-money-fraud-rules", t, "mobile-money-fraud-rules",
                "Evaluates mobile-money transactions against sealed, configurable fraud rules in real time, replays 6.3 "
                "million PaySim transactions in parallel, and measures each rule's precision and recall.",
                [("switch", "Payment switch / wallet app"), ("analyst", "Fraud analysts")],
                job="replays (async batch)", redis=True, files=True,
                controllers=["EvaluationController", "ReplayController", "RuleController"],
                services=["RuleEngine\n(sealed FraudRule)", "AccountStateStore\n(sliding windows, bounded)", "ReplayService\n(partitioned workers)"],
                repositories=["VerdictRepository (batch)", "RuleConfigRepository", "Performance queries"])
    lg = logical(t, "**Logical architecture**: the *rule engine* combines independent rules into a verdict with reason "
                 "codes; stateful rules read and update per-account *sliding windows*; the *replay* path partitions by "
                 "account so each account's events stay in order while partitions run in parallel; *analytics* compute "
                 "precision and recall per rule against labels.",
                 [G("Real time", [N("eval", "Evaluate transaction", "service"), N("engine", "Rule engine\nverdict + reasons", "service"),
                                  N("state", "Account state\nsliding windows", "comp")]),
                  G("Batch replay", [N("reader", "Streaming reader", "comp"), N("part", "Partition by account\nN ordered workers", "job"),
                                     N("perf", "Rule performance\nprecision · recall", "service")]),
                  G("Data", [N("ver", "verdict\n(non-Allow only)", "db"), N("cfg", "rule_config\n(versioned)", "db"), N("win", "Windows\n(memory or Redis)", "store")])],
                 [E("eval", "engine"), E("engine", "state"), E("state", "win"), E("engine", "cfg", "read"), E("engine", "ver"),
                  E("reader", "part"), E("part", "engine"), E("perf", "ver")])
    sol = solution(t, "**Solution architecture**: the switch calls the engine inline (sub-50 ms) before authorising a "
                   "transfer or cash-out; analysts tune rules and review flagged activity; replays against PaySim provide "
                   "evidence before a rule goes live.",
                   [N("sw", "Payment switch / wallet", "actor"), N("an", "Fraud analysts", "actor")],
                   [N("api", "Fraud rules API", "service"), N("db", "Verdicts and rules", "db"), N("rd", "State store (Redis)", "store")],
                   [N("paysim", "PaySim dataset\n(CC BY-SA 4.0)", "ext"), N("case", "Case management\n(downstream)", "ext")],
                   [E("sw", "api", "evaluate (sync)"), E("an", "api", "tune rules, replay"), E("api", "db"), E("api", "rd"),
                    E("paysim", "api", "replay input"), E("api", "case", "Review/Block events", True)])
    dec = [modular_monolith(t),
           Decision("Sealed rule hierarchy with exhaustive evaluation",
                    "Rules evolve often; a new rule type must be handled everywhere (evaluation, config, reporting).",
                    "Model rules and verdicts as sealed interfaces of records; evaluate with pattern-matching switches.",
                    "Compiler-guided changes; explainable reason codes.", "Scripting-language rules (harder to test and secure)."),
           Decision("Partition by account to keep order while running in parallel",
                    "Velocity and mule rules depend on event order per account.",
                    "Hash origin account to one of N single-threaded workers fed by bounded queues.",
                    "Deterministic results equal to sequential replay; back-pressure built in.", "Unordered parallel streams (wrong results)."),
           Decision("Bounded state: in-memory windows with eviction, Redis when multi-instance",
                    "Stateful rules can exhaust memory over millions of events.",
                    "Keep per-account windows bounded by simulated time and evict; switch to Redis sorted sets for multi-instance real time.",
                    "Stable heap; horizontal scaling path.", "Unbounded maps (OOM)."),
           Decision("Persist only non-Allow verdicts",
                    "Writing millions of Allow verdicts makes the database the bottleneck.",
                    "Store Review/Block verdicts and aggregate counts; metrics carry totals.",
                    "Fast replays; less storage. Allow decisions aren't individually auditable here (acceptable for a rules lab).",
                    "Persisting every verdict.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 8. AML
def aml() -> Architecture:
    t = "AML transaction monitoring"
    p = Profile("08-aml-transaction-monitoring", t, "aml-transaction-monitoring",
                "Detects laundering typologies (structuring, fan-in/out, rapid movement, cycles) with versioned SQL "
                "scenarios over millions of transactions, with deduplicated alerts and an immutable case workflow.",
                [("analyst", "AML analysts"), ("mlro", "MLRO")],
                job="scenario runs (scheduled / on demand)", files=True,
                controllers=["ScenarioRunController", "AlertController", "CaseController", "ReportController"],
                services=["LoaderService\n(COPY → staging → transform)", "ScenarioRunner\n(versioned SQL files)", "CaseService\n(append-only decisions)"],
                repositories=["JdbcClient + scenario SQL", "AlertRepository (dedup key)", "CaseRepository"])
    lg = logical(t, "**Logical architecture**: *loading* moves raw files through a staging table into a clean transaction "
                 "model; *detection* runs each scenario (a versioned SQL file with named parameters) over a window and "
                 "emits deduplicated alerts with evidence; *investigation* groups alerts into cases with append-only "
                 "decisions; *effectiveness* compares alerts with labels.",
                 [G("Loading", [N("stg", "Staging (COPY)", "service"), N("tx", "Transform\naccount · txn", "service")]),
                  G("Detection", [N("sc", "Scenarios\nstructuring · fan-in/out · rapid · cycles", "service"),
                                  N("run", "Scenario run\nwindow + thresholds", "job"), N("al", "Alerts\n(dedup key)", "comp")]),
                  G("Investigation", [N("case", "Cases", "service"), N("dec", "Decisions\nappend-only", "comp"),
                                      N("eff", "Effectiveness\nvs labels", "comp")]),
                  G("Data", [N("txn", "txn (millions)\nindexed per scenario", "db"), N("alerts", "alert · evidence", "db"), N("cases", "case · decision", "db")])],
                 [E("stg", "tx"), E("tx", "txn"), E("run", "sc"), E("sc", "txn", "window functions, recursive CTE"), E("sc", "al"),
                  E("al", "alerts"), E("case", "alerts"), E("dec", "cases"), E("eff", "alerts")])
    sol = solution(t, "**Solution architecture**: transaction data arrives in batches from core systems (here the IBM "
                   "AMLworld dataset); analysts work alerts and cases; the MLRO decides on suspicious transaction reports "
                   "to the financial intelligence unit (the NFIU in Nigeria).",
                   [N("an", "AML analysts", "actor"), N("mlro", "MLRO", "actor")],
                   [N("api", "AML monitoring API", "service"), N("db", "Monitoring DB", "db"), N("job", "Scenario runs", "job")],
                   [N("ibm", "IBM AMLworld dataset\n(CDLA-Sharing-1.0)", "ext"), N("fiu", "Financial intelligence unit\n(STR filing, out of scope)", "ext")],
                   [E("ibm", "api", "batch load"), E("job", "db", "SQL scenarios"), E("an", "api", "alerts, cases"),
                    E("mlro", "api", "decisions"), E("api", "db"), E("mlro", "fiu", "STR", True)])
    dec = [modular_monolith(t),
           Decision("Detection logic as versioned SQL scenarios",
                    "Typologies are set-based patterns over large windows; compliance needs to read and review them.",
                    "One SQL file per scenario with named parameters, executed via JdbcClient; thresholds and versions stored on each alert.",
                    "Fast, reviewable and testable on fixtures; requires strong SQL and indexing skills.", "Java loops over millions of rows."),
           Decision("Deduplicated alerts by scenario, subject and window",
                    "Re-running a scenario must not flood analysts with duplicates.",
                    "Unique key on (scenario, subject, window); insert with ON CONFLICT DO NOTHING.", "Idempotent runs.", "Application-side dedup."),
           Decision("Append-only decisions enforced by the database",
                    "Case decisions are legal records.",
                    "Revoke UPDATE/DELETE from the application role (or triggers) on decision tables.",
                    "Tamper-evident history even if the application has bugs.", "Soft deletes in code only."),
           Decision("Staging + transform loading with COPY",
                    "Millions of rows must load quickly and repeatably.",
                    "COPY raw files into a staging table, then transform into the model with SQL in one transaction.",
                    "Fastest load path; easy reprocessing.", "Row-by-row inserts.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 9. LEI
def lei() -> Architecture:
    t = "KYB legal entity lookup service"
    p = Profile("09-kyb-lei-lookup-service", t, "kyb-lei-lookup-service",
                "Bulk-loads the GLEIF LEI golden copy, applies delta files, validates LEI check digits, and serves fast "
                "lookups and fuzzy name search for corporate onboarding.",
                [("onb", "Corporate onboarding"), ("pay", "Payments / downstream systems")],
                externals=[("gleif", "GLEIF golden copy + deltas", "HTTPS")], job="delta imports (every 8 h)", redis=True, files=True,
                controllers=["EntityController", "ValidationController", "ImportAdminController"],
                services=["GoldenCopyLoader\n(COPY → staging → upsert)", "LeiValidator\n(ISO 7064 MOD 97-10)", "EntitySearchService"],
                repositories=["LeiRecordRepository\n(B-tree + trigram GIN)", "ImportRunRepository", "Search queries (SQL)"])
    lg = logical(t, "**Logical architecture**: *ingestion* loads the full file into staging and upserts records only when "
                 "the source is newer, then applies deltas the same way; *validation* checks ISO 17442 check digits and "
                 "status; *lookup and search* serve exact LEI reads and fuzzy name search from indexes; a cache absorbs "
                 "hot lookups and is invalidated by deltas.",
                 [G("Ingestion", [N("full", "Full load\nCOPY → staging", "service"), N("delta", "Delta apply\nnewer-wins upsert", "service")]),
                  G("Serving", [N("valid", "Validate LEI\ncheck digits + status", "service"), N("look", "Lookup by LEI", "service"),
                                N("search", "Search\nname (trigram) · country · status", "service")]),
                  G("Data", [N("rec", "lei_record\n2.5M+ rows", "db"), N("rel", "relationship\n(parents, stretch)", "db"),
                             N("cache", "Lookup cache", "store")])],
                 [E("full", "rec"), E("delta", "rec"), E("delta", "cache", "invalidate", True), E("look", "cache"), E("cache", "rec", "miss", True),
                  E("search", "rec"), E("valid", "rec"), E("full", "rel", "", True)])
    sol = solution(t, "**Solution architecture**: onboarding officers search and verify legal entities; payment systems "
                   "validate LEIs on messages in bulk; GLEIF publishes the global reference data several times a day.",
                   [N("onb", "Corporate onboarding", "actor"), N("pay", "Payments (bulk validation)", "actor")],
                   [N("api", "LEI lookup API", "service"), N("db", "LEI reference DB", "db"), N("rd", "Cache", "store")],
                   [N("gleif", "GLEIF golden copy · deltas · API\n(CC0)", "ext")],
                   [E("onb", "api", "search, lookup"), E("pay", "api", "validate up to 1,000 LEIs"), E("api", "db"), E("api", "rd"),
                    E("gleif", "api", "full + delta files")])
    dec = [modular_monolith(t),
           Decision("Staging and newer-wins upserts",
                    "Full files and deltas overlap; reads must never see a half-loaded table.",
                    "Load into staging with COPY, then INSERT ... ON CONFLICT (lei) DO UPDATE WHERE excluded.last_update > lei_record.last_update, in batches.",
                    "Repeatable loads; reads continue during imports.", "Truncate-and-reload (downtime)."),
           Decision("Trigram GIN index for fuzzy name search",
                    "Users type partial, misspelt and reordered company names.",
                    "Normalise names and index them with pg_trgm GIN; combine with B-tree filters for country and status.",
                    "Sub-100 ms fuzzy search without a search cluster.", "Elasticsearch/OpenSearch."),
           Decision("Validate check digits before trusting any LEI",
                    "Typos in LEIs are common in payment messages.",
                    "Implement ISO 7064 MOD 97-10 validation and report invalid records instead of loading them.",
                    "Catches typos early; cheap.", "Trusting input and failing lookups later.")]
    return build(p, lg, sol, dec)


# ---------------------------------------------------------------------------------------------- 10. Recon
def recon() -> Architecture:
    t = "Settlement reconciliation engine"
    p = Profile("10-settlement-reconciliation-engine", t, "settlement-reconciliation-engine",
                "Matches an internal ledger extract against partner settlement files with configurable multi-pass rules, "
                "classifies breaks, proves control totals, and supports an audited resolution workflow.",
                [("ops", "Reconciliation operations"), ("fin", "Finance")],
                externals=[("partner", "Partner settlement files\n(switch, scheme, wallet)", "SFTP / upload"),
                           ("ledger", "Core ledger extract\n(Capstone 1)", "HTTPS / file")],
                job="recon runs (async)", files=True,
                controllers=["FileController (multipart)", "ReconRunController", "BreakController"],
                services=["FileIntakeService\n(checksum · parse · staging)", "MatchingService\n(passes: exact SQL → fuzzy Java)", "BreakService\n(classification · resolution)"],
                repositories=["Staging tables (COPY)", "MatchRepository", "BreakRepository"])
    lg = logical(t, "**Logical architecture**: *intake* stores each file once (by checksum) and parses it into staging; "
                 "*matching* runs ordered passes over the remaining unmatched items (exact in SQL, then normalised "
                 "reference, tolerance and date-window passes); *break management* classifies and resolves exceptions; "
                 "*controls* prove that matched plus unmatched value equals each file's total.",
                 [G("Intake", [N("up", "Upload / fetch file", "service"), N("ck", "Checksum dedup", "comp"), N("parse", "Parse → staging", "service")]),
                  G("Matching", [N("p1", "Pass 1: exact\nFULL OUTER JOIN", "service"), N("p2", "Pass 2–4\nnormalised ref · tolerance · date window", "service")]),
                  G("Exceptions and controls", [N("brk", "Break classification", "service"), N("res", "Resolution workflow\naudited", "comp"),
                                                N("ctl", "Control totals", "comp")]),
                  G("Data", [N("files", "File store\n(object storage)", "store"), N("stg", "ledger_item · partner_item", "db"),
                             N("mt", "match · break · resolution", "db")])],
                 [E("up", "ck"), E("ck", "files"), E("ck", "parse"), E("parse", "stg"), E("p1", "stg"), E("p1", "p2", "unmatched"),
                  E("p2", "mt"), E("brk", "mt"), E("res", "mt"), E("ctl", "stg")])
    sol = solution(t, "**Solution architecture**: every value date, operations reconcile the bank's ledger with each "
                   "partner's settlement file; breaks are worked to resolution before cut-off and summarised for finance.",
                   [N("ops", "Reconciliation operations", "actor"), N("fin", "Finance", "actor")],
                   [N("api", "Reconciliation API", "service"), N("db", "Recon DB", "db"), N("obj", "File storage", "store")],
                   [N("partner", "Partner settlement files", "ext"), N("ledger", "Core ledger (Capstone 1)\nextract", "ext"),
                    N("paysim", "PaySim base data\n(CC BY-SA 4.0)", "ext")],
                   [E("partner", "api", "daily files"), E("ledger", "api", "extract"), E("paysim", "api", "test data generator", True),
                    E("ops", "api", "runs, breaks"), E("fin", "api", "reports"), E("api", "db"), E("api", "obj")])
    dec = [modular_monolith(t),
           Decision("Ordered matching passes, set-based first",
                    "Most items match exactly; the rest need fuzzier rules that are expensive.",
                    "Run an exact FULL OUTER JOIN in SQL first, then normalised-reference, tolerance and date-window passes only on what remains.",
                    "Fast and explainable (each match records its pass).", "One fuzzy pass over everything."),
           Decision("Control totals on every run",
                    "A reconciliation that silently drops items is worse than none.",
                    "Assert matched + unmatched value equals each side's file total; fail the run otherwise.",
                    "Provable completeness.", "Trusting counts."),
           Decision("Files in object storage, idempotent by checksum",
                    "Files are large and must not be processed twice.",
                    "Store originals in S3/Blob/GCS (a volume on the VPS) keyed by SHA-256; reject re-uploads of the same checksum.",
                    "Evidence retained; safe retries.", "Storing files in the database.")]
    return build(p, lg, sol, dec)


ALL = [ledger, fx, loans, scorecard, complaints, screening, fraud, aml, lei, recon]
