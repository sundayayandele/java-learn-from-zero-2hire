# Reference solution: FX reference rates service

← [Capstone brief](../README.md)

> **Build your own first.** Then compare decisions: where rounding happens, how the import stays idempotent and single-runner, and why the cache lives in its own bean.

Java 21 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · JdbcClient/JdbcTemplate · RestClient · Caffeine cache · Testcontainers. 15 tests, all green. A live import of the full ECB history (7,120 publication days, 221,528 rates) takes about 30 seconds including the download.

## Run it

```bash
cp .env.example .env            # set DB_PASSWORD
docker compose up --build
curl -s -X POST localhost:8080/api/v1/admin/imports          # download and import the ECB history
curl -s "localhost:8080/api/v1/rates/GBP/USD?date=2026-10-22"
curl -s "localhost:8080/api/v1/conversions?from=EUR&to=JPY&amount=250&date=2026-10-18"   # Sunday → Friday's rate
curl -s "localhost:8080/api/v1/rates/EUR/USD/history?from=2026-01-01&to=2026-03-31"
curl -s localhost:8080/api/v1/currencies
curl -s localhost:8080/actuator/prometheus | grep cache_gets
```

For air-gapped environments, upload an ECB-format CSV instead: `curl -X POST -H 'Content-Type: text/csv' --data-binary @eurofxref-hist.csv localhost:8080/api/v1/admin/imports/csv`.

## Design

```text
EcbDownloader (RestClient, timeouts, @Scheduled weekdays 16:45 Europe/Berlin)
   └─► RateImporter  @Transactional + pg_try_advisory_xact_lock → EcbCsvParser (streaming) → JDBC batches
                      INSERT ... ON CONFLICT (currency, rate_date) DO NOTHING   → evicts caches
       ImportRunLog   REQUIRES_NEW transactions, so failed runs are recorded too
RatesController ─► RateService (validation, business-day fallback, cross-rate maths)
                      └─► RateRepository (@Cacheable, Caffeine with recordStats → cache.gets metrics)
```

### Decisions

1. **Store per-EUR, derive everything else.** The table holds exactly what the ECB publishes. Inverse and cross rates are computed on read (`perEur(quote) ÷ perEur(base)`, 10 decimal places, `HALF_EVEN`) and flagged `derived: true`, which keeps faith with the ECB's "statistics must not be modified" condition.
2. **Convert in one step, round once.** `amount × perEur(to) ÷ perEur(from)` rounded straight to the target currency's minor units (`Currency.getDefaultFractionDigits()`), so there is no double rounding through an intermediate rate.
3. **Business-day fallback is explicit.** Every response carries `requestedDate` and `rateDate`. Dates before 1999-01-04, or more than 7 days after the latest import (stale data), are refused with 422.
4. **Idempotent, single-runner import.** `ON CONFLICT DO NOTHING` makes re-imports harmless; `pg_try_advisory_xact_lock` stops two instances importing at once (the second gets 409, or skips quietly when scheduled). The import log uses `REQUIRES_NEW` so a failed import is still recorded.
5. **Streaming parser with line numbers.** Handles the ECB's trailing comma and `N/A` cells; errors say which line failed.
6. **Caching through a separate bean.** `@Cacheable` sits on `RateRepository`, not on `RateService` methods called internally, because self-invocation would bypass the proxy. Imports evict both caches. Caffeine records stats, so `cache.gets{result=hit|miss}` appears in Prometheus.
7. **Scheduling and scale-to-zero.** `@Scheduled` suits a VPS or always-on container. On Cloud Run or Azure Container Apps, set `FX_IMPORT_SCHEDULE_ENABLED=false` and call `POST /api/v1/admin/imports` from Cloud Scheduler or a Container Apps job instead.

### What I'd do next

- Add the US Treasury dataset so NGN is available, with a source priority per currency.
- Use PostgreSQL `COPY` for the initial load (several times faster than batched inserts).
- `ETag` support on `/rates/latest` for mobile clients.
- Secure the admin endpoints (Module 6).

## Tests

| Test | What it proves |
| --- | --- |
| `EcbCsvParserTest` | Real-format parsing (141 values from 5 rows), `N/A` skipped, line numbers in errors, header check |
| `FxApiIntegrationTest` | Idempotent re-import, exact direct rates, inverse and cross rates at 10 dp, weekend fallback, default date from an injected `Clock`, JPY rounding, Problem Details for unknown currency / unavailable rate / out-of-range dates / bad format, history, cache hits |
| `FxRatesApplicationTests` | Migrations ran, readiness UP, Problem Details for unknown paths |

## Data and attribution

Source: ECB statistics. Euro foreign exchange reference rates, <https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html>, reused under the [ESCB policy on reuse of statistics](https://www.ecb.europa.eu/stats/ecb_statistics/governance_and_quality_framework/html/usage_policy.en.html). Inverse rates, cross rates and conversions are derived by this service and are not ECB statistics. The test fixture contains five unmodified rows of the ECB file.
