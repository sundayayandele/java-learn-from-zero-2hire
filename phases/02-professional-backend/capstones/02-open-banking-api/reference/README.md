# Reference solution: Secure open banking accounts API

← [Capstone brief](../README.md) · [Build guide](../GUIDE.md) · [Architecture](../architecture/README.md)

> **Build your own version first.** Compare after you've finished, and be ready to explain your different choices.

Tested with Java 21, Spring Boot 4.1.2, Spring Security 7.1 and PostgreSQL 17: **26 tests, 90.2% line coverage** (the build enforces at least 80%).

## Run it

```bash
cp .env.example .env                         # set DB_PASSWORD and KEYCLOAK_ADMIN_PASSWORD
docker compose up --build -d
# Seed alice, bob and 200 synthetic customers (about 100,000 transactions) once:
docker compose run --rm -e SPRING_PROFILES_ACTIVE=seed app
DEV_USER_PASSWORD='<pick one>' KEYCLOAK_ADMIN_PASSWORD='<same as .env>' ./scripts/dev-users.sh   # also creates the TPP client and prints its secret
```

Then walk the flow:

```bash
# 1. The TPP (client credentials) creates a consent
TPP=$(curl -s -d grant_type=client_credentials -d client_id=tpp-budgetapp -d client_secret="$TPP_SECRET" -d scope=accounts \
  http://localhost:8081/realms/itl/protocol/openid-connect/token | jq -r .access_token)
CONSENT=$(curl -s -X POST http://localhost:8080/open-banking/v1/account-access-consents -H "Authorization: Bearer $TPP" \
  -H 'Content-Type: application/json' \
  -d "{\"permissions\":[\"ReadAccountsDetail\",\"ReadBalances\",\"ReadTransactionsDetail\"],\"expirationDateTime\":\"$(date -u -v+30d +%Y-%m-%dT%H:%M:%SZ)\"}" | jq -r .consentId)

# 2. Alice (a customer) approves it for her current account
ALICE=$(curl -s -d client_id=itl-cli -d grant_type=password -d username=alice -d password="$DEV_USER_PASSWORD" -d scope=openid \
  http://localhost:8081/realms/itl/protocol/openid-connect/token | jq -r .access_token)
curl -s -X POST http://localhost:8080/customer/v1/consents/$CONSENT/authorise -H "Authorization: Bearer $ALICE" \
  -H 'Content-Type: application/json' -d '{"accountIds":["acc-alice-current"]}' | jq

# 3. The TPP reads only what was approved
curl -s http://localhost:8080/open-banking/v1/accounts -H "Authorization: Bearer $TPP" -H "x-consent-id: $CONSENT" | jq
```

(`date -v+30d` is macOS; on Linux use `date -u -d '+30 days' +%Y-%m-%dT%H:%M:%SZ`.)

## How it's built

| Package | Responsibility |
| --- | --- |
| `consent` | `Consent` (lifecycle rules, expiry derived from time, never stored), `ConsentService`, `ConsentGuard` (the one gate for every data request), `ConsentRepository` (including the atomic daily call counter) |
| `account` | `AccountInfoService` (least data: Basic vs Detail, consent window ∩ request window, unattended limit), `AccountRepository` (keyset pagination on `(booked_at, id)`) |
| `audit` | `AccessLog`: append-only (database trigger), written in its own transaction so denials are kept |
| `web` | TPP, customer and ops controllers; Problem Details with `Retry-After` on 429 |
| `security` | Resource server; `CurrentUser.clientId()` (`azp`) for TPPs, `CurrentUser.username()` for customers |
| `seed` | Deterministic synthetic data (CC0) |

## Design notes

- **Two kinds of callers, two identities.** TPPs are OAuth clients (`azp`), customers are users. A TPP token can never reach customer endpoints (`ROLE_CUSTOMER`) and vice versa (`SCOPE_accounts`).
- **Another TPP's consent returns 404, not 403**, so consent ids can't be probed. Accounts outside the consent return 403 `account-not-in-consent` without revealing whether they exist.
- **Expiry is computed, not stored**, so there's no job that might fail to expire a consent in time.
- **Customers are matched by `preferred_username`** to keep local setup simple (the realm doesn't allow editing usernames). A production bank would map the token's `sub` to its customer record during onboarding.
- The unattended limit counts per consent, account, endpoint and Lagos calendar day with one `INSERT ... ON CONFLICT ... RETURNING`, so it is correct under concurrency without locks. Calls with `x-fapi-customer-ip-address` (customer present) aren't counted.

## What I'd do next

- DPoP or mTLS sender-constrained tokens for TPPs (FAPI), and signed consent requests.
- A consent dashboard page in the bank's app, and email notifications on new and revoked consents.
- Partition `access_log` by month and ship it to the bank's SIEM.

Specification credit: the consent model and permission names follow the [UK Open Banking Read/Write API](https://standards.openbanking.org.uk/api-specifications/) (simplified). All data is synthetic (CC0).
