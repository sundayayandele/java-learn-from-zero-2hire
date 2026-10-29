# Java Backend Engineer: Zero to Hired

A self-paced, project-based path from no programming experience to a hireable Java backend engineer, built on the problems banks and fintechs actually hire for: ledgers, payments, FX, lending, fraud, AML, KYB and reconciliation.

It implements the [ITLearning Java Backend Engineer Training Program](https://itlearning.ng/java-backend-engineer-training-program/) topic for topic, adds Kotlin, and is delivered through the ITLearning Moodle LMS at itlearning.tech, with this repository as the source of truth.

| | |
| --- | --- |
| Stack | Java 21 LTS (Java 25 noted where relevant) · Spring Boot 4.1 · Kotlin 2.4 · Maven · PostgreSQL 17 · Redis · MongoDB · Docker |
| CI/CD | GitHub Actions (primary) · GitLab CI (secondary) · Azure DevOps · AWS CodeBuild/CodePipeline · Jenkins · Argo CD |
| Deployment | VPS (e.g. SSDNodes) · AWS · Azure · GCP |
| Duration | Junior-ready after Phase 2: 26 scheduled weeks at 16–27 hours a week (about 470–565 hours), or about 40–45 weeks at 12–15 hours a week. Phase 3 (intermediate) follows. An optional week-0 on-ramp is there for complete beginners |

## Status

| Phase | Weeks | Status |
| --- | --- | --- |
| [Phase 1: Foundations](phases/01-foundations/README.md) | 0–14 | **Built**: a week-0 on-ramp, 35 topics (4 optional), 10 capstones, exam, defence, live coding, Getting Hired |
| [Phase 2: Professional Backend](phases/02-professional-backend/README.md) | 15–25 | **Built**: 27 topics, 10 capstones, 5 labs, exam, defence, live coding, Getting Hired |
| [Phase 3: Enterprise & Cloud-Native](phases/03-enterprise-cloud-native/README.md) | 26–37 | Mapped, to be built after the Phase 1 review |

The [full curriculum](CURRICULUM.md) lists every phase, module and topic.

## What's in Phase 1

- **A week-0 on-ramp** for complete beginners (first programs, loops, arrays, strings and methods), with a 15-question entry check that lets experienced learners skip it.
- **35 topics** (the website's topics; Git, unit testing and HTTP basics taught early, where the projects first need them; Kotlin, three topics that complete the Oracle OCP Java SE 21/25 objectives, and three interview essentials: design patterns, debugging and troubleshooting, and Spring under the hood). Four are optional: Kotlin, Spring Boot with Kotlin, localization, and modules and `jlink`, each with authored reading content, curated and verified YouTube videos (English, captioned, embeddable), practice tasks and a **scenario-based CBT** (6–10 questions, 281 in total including the on-ramp).
- **Module projects** from the ITLearning programme (wallet simulator, payroll, parallel transaction processor, ledger schema, query-tuning lab, task API, Spring Boot 3 → 4 upgrade and more), plus three hands-on [labs](labs/).
- **10 capstones** on financial-institution use cases using public data free for commercial and non-commercial use, each licence recorded in [docs/data-sources.md](docs/data-sources.md). Every capstone has a generated, tested Spring Boot 4 **starter**; Capstones 1 (core banking ledger) and 2 (FX rates) have complete, tested **reference solutions**, which mentors share once a learner's submission has passed.
- **Deployment and CI/CD guide** with infrastructure sizing and step-by-step setup for a VPS, AWS, Azure and GCP.
- A **2-hour, 60-question end-of-phase exam** (bank kept private; [blueprint here](phases/01-foundations/exam/README.md)).
- A **Getting Hired** section modelled on the FDE Zero-to-Hired programme.
- An **Oracle certification map** ([docs/certifications.md](docs/certifications.md)): every OCP Java SE 21/25 objective mapped to a topic.
- **CI/CD on six platforms** (GitHub Actions, GitLab, Azure DevOps, AWS CodeBuild/CodePipeline, Jenkins, Argo CD) and **architecture and system-design packs** for every capstone (solution, logical, technical, infrastructure and cloud diagrams in Mermaid, draw.io, SVG and PNG, plus ADRs).

## What's in Phase 2

- **27 topics** across API design and integration (including system design basics), security, testing and code quality, Git/Docker/CI/CD (including working in an agile team), and AI-assisted engineering with the junior portfolio, each with a full lesson, verified videos and a **scenario-based CBT** (192 questions in total).
- **Five broken-service labs** (resilience, security, flaky tests, containers and pipelines, reviewing an AI-generated pull request), each with failing tests or an automatic checker.
- **10 integration- and security-heavy capstones** (payment gateway hub, open banking, card vault, KYC orchestration, merchant API keys, SEC financials GraphQL, interbank gRPC, bill payments, mortgage decisions, fraud case management) on public data or simulators. Every starter is generated from the new [secure service starter](templates/spring-boot-secure-service-starter/README.md) (Keycloak, OAuth2 resource server, OpenAPI, JaCoCo gate, SBOM, Trivy scans); Capstones 1 and 2 have complete, tested reference solutions, shared by mentors after a pass.
- **Deployment guide with an identity provider**, a **CI/CD quality-gates guide** for all six platforms, a **60-question exam** ([blueprint](phases/02-professional-backend/exam/README.md)) and a junior-launch **Getting Hired** guide.

## Across both phases

- **[Coding-interview practice track](practice/README.md)**: 127 problems over weeks 3–25: 46 auto-graded Java problems, 24 auto-graded PostgreSQL drills (Testcontainers) and 57 mapped classic problems, so learners pass the online assessments that open most hiring loops.
- **Assessments that test production, not just recognition**: a [capstone defence](docs/assessments/capstone-defence.md) (viva with a live change) and a [timed live-coding assessment](docs/assessments/live-coding.md) each phase, and exams drawn at random from 200-question banks.
- **[Java fundamentals revision sheet](docs/interview/java-fundamentals.md)** for interview week.

## How to study

Start at [Phase 1](phases/01-foundations/README.md). For each topic: read and code along, watch the videos, take the CBT, do the practice tasks. Each module has a project; from week 6 you build capstones. On Moodle the same content appears as pages and graded quizzes, with progress tracking and certificates.

## Repository layout

```text
curriculum.json                 phases, modules and topics (source of truth for structure)
phases/01-foundations/          Phase 1: modules, topics (lesson.md + cbt.json), capstones, deploy, exam, getting-hired
phases/02-professional-backend/ Phase 2: same structure, plus cicd/ (quality gates)
practice/                       coding-interview practice track: dsa/ (Java + JUnit) and sql/ (PostgreSQL drills)
docs/assessments/, docs/interview/  capstone defence, live coding, fundamentals revision sheet
phases/03-...                   planned phase (topic list)
templates/                      Spring Boot 4 service starter, secure service starter (Phase 2) and Java CLI starter
labs/                           hands-on labs (Phase 1 projects, Phase 2 broken-service labs)
lms/videos.json                 every video, verified by lms/check_videos.py
lms/moodle/BLUEPRINT.md         how the Moodle course is set up
scripts/build.py                generates the README pages from the sources
scripts/check_repo.py           structure, links, generated files and quiz checks (run in CI)
scripts/new_starter.py          generates each capstone's starter from the template
scripts/moodle/                 Moodle XML / page builder and web-services push script
docs/data-sources.md            datasets and licences
```

## Maintaining the content

```bash
python scripts/build.py              # regenerate READMEs after editing lesson.md, cbt.json, guide.md or videos.json
python scripts/new_starter.py        # regenerate capstone starters after changing the template
python scripts/practice_stubs.py     # regenerate practice stubs after editing practice/*/solutions
python scripts/check_repo.py         # everything consistent? (CI runs this)
python lms/check_videos.py --quick   # are all videos still online and embeddable?
python scripts/moodle/build_moodle.py   # Moodle import files (needs pandoc)
```

Generated files carry a banner saying so; edit their sources instead.

## Licence and attribution

Course content is © ITLearning. Datasets keep their own licences, listed in [docs/data-sources.md](docs/data-sources.md). Videos are linked and embedded from YouTube and belong to their creators.
