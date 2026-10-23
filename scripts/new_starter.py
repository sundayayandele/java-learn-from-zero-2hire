#!/usr/bin/env python3
"""Generate each capstone's starter/ folder from templates/spring-boot-service-starter.

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
TEMPLATE = ROOT / "templates" / "spring-boot-service-starter"
CAPSTONES = ROOT / "phases" / "01-foundations" / "capstones"
SKIP = {"target", ".idea"}

# slug -> (package suffix, application class prefix, title)
STARTERS = {
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

README = """# Starter: {title}

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
- [ ] Add your `@ConfigurationProperties` record for thresholds, limits and URLs (topic 4.4).
- [ ] Build the API with DTO records, validation and a Problem Detail per domain exception (topic 4.2).
- [ ] Add at least two business metrics (topic 4.5).
- [ ] Write the acceptance tests listed in the brief, then make them pass.
- [ ] Deploy with the [deployment guide](../../../deploy/README.md) and put the health URL in your README.
- [ ] Copy the data source attribution from the brief into your README.
"""


def render(slug: str, dest: Path) -> None:
    pkg, cls, title = STARTERS[slug]
    artifact = slug.split("-", 1)[1]
    app_class = f"{cls}Application"
    if dest.exists():
        shutil.rmtree(dest)
    shutil.copytree(TEMPLATE, dest, ignore=shutil.ignore_patterns(*SKIP))
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
        ("spring-boot-service-starter", artifact),
        ("Java Zero-to-Hired: Spring Boot 4 service starter", f"Java Zero-to-Hired capstone: {title}"),
    ]
    for f in dest.rglob("*"):
        if f.is_file() and (f.suffix in {".java", ".xml", ".yaml", ".yml", ".sql", ".md"} or f.name == "Jenkinsfile") \
                and f.name != "README.md":
            text = f.read_text(encoding="utf-8")
            for a, b in replacements:
                text = text.replace(a, b)
            f.write_text(text, encoding="utf-8")
    (dest / "README.md").write_text(
        README.format(title=title, artifact=artifact, app_class=app_class), encoding="utf-8")


def same_tree(a: Path, b: Path) -> bool:
    cmp = filecmp.dircmp(a, b, ignore=list(SKIP))
    if cmp.left_only or cmp.right_only or cmp.diff_files or cmp.funny_files:
        return False
    for sub in cmp.common_dirs:
        if not same_tree(a / sub, b / sub):
            return False
    return True


def main(argv: list[str]) -> int:
    stale = []
    for slug in STARTERS:
        target = CAPSTONES / slug / "starter"
        if "--check" in argv:
            with tempfile.TemporaryDirectory() as tmp:
                fresh = Path(tmp) / "starter"
                render(slug, fresh)
                if not target.exists() or not same_tree(fresh, target):
                    stale.append(slug)
        else:
            render(slug, target)
    if "--check" in argv:
        for s in stale:
            print(f"stale starter: {s} (run python scripts/new_starter.py)", file=sys.stderr)
        return 1 if stale else 0
    print(f"starters: {len(STARTERS)} generated")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
