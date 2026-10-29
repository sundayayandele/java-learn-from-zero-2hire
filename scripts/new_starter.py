#!/usr/bin/env python3
"""Generate each capstone's starter/ folder from its phase's template.

Phase 1 capstones use templates/spring-boot-service-starter; Phase 2 capstones use
templates/spring-boot-secure-service-starter (security, OpenAPI and CI quality gates).

    python scripts/new_starter.py            # (re)generate every capstone starter
    python scripts/new_starter.py --check    # exit 1 if any starter differs from the template

Each starter is the template with its own package, artifactId, application class and README.
"""
from __future__ import annotations

import filecmp
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP = {"target", ".idea"}

# slug -> (package suffix, application class prefix, title)
STARTERS_P1 = {
    "01-core-banking-ledger": ("ledger", "Ledger", "Core banking ledger"),
    "02-fx-rates-service": ("fxrates", "FxRates", "FX reference rates service"),
    "03-loan-amortisation-service": ("loans", "Loans", "Loan amortisation and repayment service"),
    "04-credit-risk-scorecard-api": ("scorecard", "Scorecard", "Credit-card default risk scorecard API"),
    "05-complaints-triage-service": ("complaints", "Complaints", "Consumer complaints triage and SLA service"),
    "06-sanctions-screening-service": ("screening", "Screening", "Sanctions screening service"),
    "07-mobile-money-fraud-rules": ("fraudrules", "FraudRules", "Mobile money fraud rules engine"),
    "08-aml-transaction-monitoring": ("aml", "Aml", "AML transaction monitoring"),
    "09-kyb-lei-lookup-service": ("lei", "Lei", "KYB legal entity lookup service"),
    "10-settlement-reconciliation-engine": ("recon", "Recon", "Settlement reconciliation engine"),
}

STARTERS_P2 = {
    "01-payment-gateway-hub": ("gateway", "Gateway", "Payment gateway integration hub"),
    "02-open-banking-api": ("openbanking", "OpenBanking", "Secure open banking accounts API"),
    "03-card-tokenisation-vault": ("vault", "Vault", "Card tokenisation vault"),
    "04-kyc-verification-orchestrator": ("kyc", "Kyc", "KYC identity verification orchestrator"),
    "05-merchant-api-platform": ("merchants", "Merchants", "Merchant onboarding and API key platform"),
    "06-sec-financials-graphql": ("financials", "Financials", "Company financials GraphQL API"),
    "07-interbank-transfer-grpc": ("interbank", "Interbank", "Interbank transfer gRPC service"),
    "08-bill-payments-aggregator": ("bills", "Bills", "Bill payments aggregator"),
    "09-mortgage-decision-api": ("mortgage", "Mortgage", "Mortgage application decision API"),
    "10-fraud-case-management": ("cases", "Cases", "Fraud case management service"),
}

# (capstones folder, template folder, template artifactId, starters, README text, title marker in the pom)
PHASES = [
    (ROOT / "phases" / "01-foundations" / "capstones", ROOT / "templates" / "spring-boot-service-starter",
     "spring-boot-service-starter", STARTERS_P1, "README_P1", "Java Zero-to-Hired: Spring Boot 4 service starter"),
    (ROOT / "phases" / "02-professional-backend" / "capstones", ROOT / "templates" / "spring-boot-secure-service-starter",
     "spring-boot-secure-service-starter", STARTERS_P2, "README_P2", "Java Zero-to-Hired: Spring Boot 4 secure service starter"),
]

README_P1 = """# Starter: {title}

Generated from the [Spring Boot service starter](../../../../../templates/spring-boot-service-starter/README.md) by `scripts/new_starter.py`. Read the [capstone brief](../README.md) first.

## Get going

1. Create a new **public** GitHub repository (for example `{artifact}`) and copy the contents of this folder into it, including the hidden files (`.github/`, `.gitlab-ci.yml`, `.gitignore`, `.env.example`, `.mvn/`).
2. `cp .env.example .env`, set `DB_PASSWORD`, then `docker compose up --build` and open <http://localhost:8080/actuator/health>.
3. `./mvnw verify` runs the tests (Docker must be running for Testcontainers).
4. Push to GitHub: the Actions workflow runs on every push. Optionally mirror to gitlab.com for the GitLab pipeline.

## What's already here

- `{app_class}`: the Spring Boot 4.1 application, with an injectable `Clock`.
- `web/ApiExceptionHandler`: RFC 9457 Problem Details for validation errors and unexpected errors. Add a handler per domain exception.
- `db/migration/V1__baseline.sql`: replace it with your first real migration.
- Integration tests with a real PostgreSQL (Testcontainers) proving migrations ran and readiness is UP.
- Dockerfile and Compose file.
- CI/CD for six platforms: GitHub Actions (primary, `.github/workflows/ci.yml`), GitLab CI (`.gitlab-ci.yml`), Azure DevOps (`azure-pipelines.yml`), AWS CodeBuild/CodePipeline (`buildspec.yml`), Jenkins (`Jenkinsfile`), and Kubernetes manifests with an Argo CD GitOps Application (`deploy/`). See the [CI/CD guide](../../../cicd/README.md).

## Your first tasks

- [ ] Replace `V1__baseline.sql` with your schema (topics 3.1, 3.4) and add constraints for every rule the database can enforce.
- [ ] Model the domain in plain Java first, with unit tests (records, sealed types, `BigDecimal`).
- [ ] Add your `@ConfigurationProperties` record for thresholds, limits and URLs (topic 4.5).
- [ ] Build the API with DTO records, validation and a Problem Detail per domain exception (topic 4.3).
- [ ] Add at least two business metrics (topic 4.6).
- [ ] Write the acceptance tests listed in the brief, then make them pass.
- [ ] Deploy with the [deployment guide](../../../deploy/README.md) and put the health URL in your README.
- [ ] Copy the data source attribution from the brief into your README.
"""


README_P2 = """# Starter: {title}

Generated from the [Spring Boot secure service starter](../../../../../templates/spring-boot-secure-service-starter/README.md) by `scripts/new_starter.py`. Read the [capstone brief](../README.md) and the [step-by-step guide](../GUIDE.md) first.

## Get going

1. Create a new **public** GitHub repository (for example `{artifact}`) and copy the contents of this folder into it, including the hidden files (`.github/`, `.gitlab-ci.yml`, `.gitignore`, `.env.example`, `.mvn/`).
2. `cp .env.example .env`, set `DB_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`, then `docker compose up --build`.
3. Create local test users: `DEV_USER_PASSWORD=<pick one> KEYCLOAK_ADMIN_PASSWORD=<same as .env> ./scripts/dev-users.sh`, then get a token as shown in the [template README](../../../../../templates/spring-boot-secure-service-starter/README.md#use-it).
4. `./mvnw verify` runs the tests and the coverage gate (Docker must be running for Testcontainers).
5. Push to GitHub: the Actions workflow runs tests, coverage, dependency and secret scans on every push.

## What's already here

- `{app_class}`: the Spring Boot 4.1 application.
- `security/SecurityConfig`: stateless OAuth2 resource server; scopes become `SCOPE_x`, Keycloak realm roles become `ROLE_x`; method security on.
- `security/CurrentUser`: the caller's subject, for ownership checks.
- `web/ApiExceptionHandler`: RFC 9457 Problem Details, including 403 for method-security failures.
- `keycloak/itl-realm.json`: realm `itl` with roles `CUSTOMER`, `OPS`, `ADMIN`, scopes `api.read`, `api.write`, and audience `itl-api`.
- springdoc OpenAPI at `/v3/api-docs` and `/swagger-ui.html`.
- JaCoCo coverage gate at 70% lines, CycloneDX SBOM, Trivy scans, and CI/CD for six platforms (see the [CI/CD guide](../../../cicd/README.md)).

## Your first tasks

- [ ] Write the OpenAPI contract for the brief's API first (topic 5.2) and review it before coding.
- [ ] Replace `V1__baseline.sql` with your schema and constraints.
- [ ] Add your domain model with unit tests (TDD, topic 7.1).
- [ ] Secure every endpoint with the roles and scopes in the brief, and test 401, 403 and ownership (topics 6.3, 7.3).
- [ ] Add integrations behind interfaces with timeouts, retries and WireMock tests (topics 5.4, 7.2).
- [ ] Keep `./mvnw verify` and every CI gate green on `main`.
- [ ] Deploy with the [deployment guide](../../../deploy/README.md) and put the health URL in your README.
- [ ] Copy the data and sandbox attribution from the brief into your README.
"""


def render(slug: str, dest: Path, template: Path, template_artifact: str, starters: dict, readme: str,
           title_marker: str) -> None:
    pkg, cls, title = starters[slug]
    artifact = slug.split("-", 1)[1]
    app_class = f"{cls}Application"
    if dest.exists():
        shutil.rmtree(dest)
    shutil.copytree(template, dest, ignore=shutil.ignore_patterns(*SKIP))
    old_dir_main = dest / "src/main/java/ng/itlearning/starter"
    old_dir_test = dest / "src/test/java/ng/itlearning/starter"
    new_dir_main = dest / f"src/main/java/ng/itlearning/{pkg}"
    new_dir_test = dest / f"src/test/java/ng/itlearning/{pkg}"
    old_dir_main.rename(new_dir_main)
    old_dir_test.rename(new_dir_test)
    (new_dir_main / "StarterApplication.java").rename(new_dir_main / f"{app_class}.java")
    (new_dir_test / "StarterApplicationTests.java").rename(new_dir_test / f"{app_class}Tests.java")
    replacements = [
        ("ng.itlearning.starter", f"ng.itlearning.{pkg}"),
        ("StarterApplicationTests", f"{app_class}Tests"),
        ("StarterApplication", app_class),
        (title_marker, f"Java Zero-to-Hired capstone: {title}"),
        (template_artifact, artifact),
    ]
    for f in dest.rglob("*"):
        if f.is_file() and (f.suffix in {".java", ".xml", ".yaml", ".yml", ".sql", ".md"} or f.name == "Jenkinsfile") \
                and f.name != "README.md":
            text = f.read_text(encoding="utf-8")
            for a, b in replacements:
                text = text.replace(a, b)
            f.write_text(text, encoding="utf-8")
    (dest / "README.md").write_text(
        globals()[readme].format(title=title, artifact=artifact, app_class=app_class), encoding="utf-8")


def same_tree(a: Path, b: Path) -> bool:
    cmp = filecmp.dircmp(a, b, ignore=list(SKIP))
    if cmp.left_only or cmp.right_only or cmp.diff_files or cmp.funny_files:
        return False
    for sub in cmp.common_dirs:
        if not same_tree(a / sub, b / sub):
            return False
    return True


def main(argv: list[str]) -> int:
    stale, count = [], 0
    for capstones, *spec in PHASES:
        starters = spec[2]
        for slug in starters:
            count += 1
            target = capstones / slug / "starter"
            if "--check" in argv:
                with tempfile.TemporaryDirectory() as tmp:
                    fresh = Path(tmp) / "starter"
                    render(slug, fresh, *spec)
                    if not target.exists() or not same_tree(fresh, target):
                        stale.append(slug)
            else:
                render(slug, target, *spec)
    if "--check" in argv:
        for s in stale:
            print(f"stale starter: {s} (run python scripts/new_starter.py)", file=sys.stderr)
        return 1 if stale else 0
    print(f"starters: {count} generated")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
