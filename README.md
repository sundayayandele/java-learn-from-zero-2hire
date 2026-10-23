# Java Backend Engineer: Zero to Hired

A self-paced, project-based path from no programming experience to a hireable Java backend engineer, built on the problems banks and fintechs actually hire for: ledgers, payments, FX, lending, fraud, AML, KYB and reconciliation.

It implements the [ITLearning Java Backend Engineer Training Program](https://itlearning.ng/java-backend-engineer-training-program/) topic for topic, adds Kotlin, and is delivered through the ITLearning Moodle LMS at itlearning.tech, with this repository as the source of truth.

| | |
| --- | --- |
| Stack | Java 21 LTS (Java 25 noted where relevant) · Spring Boot 4.1 · Kotlin 2.4 · Maven · PostgreSQL 17 · Redis · MongoDB · Docker |
| CI/CD | GitHub Actions (primary) · GitLab CI (secondary) · Azure DevOps · AWS CodeBuild/CodePipeline · Jenkins · Argo CD |
| Deployment | VPS (e.g. SSDNodes) · AWS · Azure · GCP |
| Duration | 36 weeks at 10–15 hours per week: junior-ready after Phase 2, intermediate-ready after Phase 3 |

## Status

| Phase | Weeks | Status |
| --- | --- | --- |
| [Phase 1: Foundations](phases/01-foundations/README.md) | 1–13 | **Built (pilot)**: 29 topics, 10 capstones, exam, Getting Hired |
| [Phase 2: Professional Backend](phases/02-professional-backend/README.md) | 14–24 | Mapped, to be built after the Phase 1 review |
| [Phase 3: Enterprise & Cloud-Native](phases/03-enterprise-cloud-native/README.md) | 25–36 | Mapped, to be built after the Phase 1 review |

The [full curriculum](CURRICULUM.md) lists every phase, module and topic.

## What's in Phase 1

- **29 topics** (the website's topics, plus Kotlin and three topics that complete the Oracle OCP Java SE 21/25 objectives), each with authored reading content, curated and verified YouTube videos (English, captioned, embeddable), practice tasks and a **scenario-based CBT** (6–8 questions, 211 in total).
- **Module projects** from the ITLearning programme (wallet simulator, payroll, parallel transaction processor, ledger schema, query-tuning lab, task API, Spring Boot 3 → 4 upgrade and more), plus three hands-on [labs](labs/).
- **10 capstones** on financial-institution use cases using public data free for commercial and non-commercial use, each licence recorded in [docs/data-sources.md](docs/data-sources.md). Every capstone has a generated, tested Spring Boot 4 **starter**; Capstones 1 (core banking ledger) and 2 (FX rates) have complete, tested **reference solutions**.
- **Deployment and CI/CD guide** with infrastructure sizing and step-by-step setup for a VPS, AWS, Azure and GCP.
- A **2-hour, 60-question end-of-phase exam** (bank kept private; [blueprint here](phases/01-foundations/exam/README.md)).
- A **Getting Hired** section modelled on the FDE Zero-to-Hired programme.
- An **Oracle certification map** ([docs/certifications.md](docs/certifications.md)): every OCP Java SE 21/25 objective mapped to a topic.
- **CI/CD on six platforms** (GitHub Actions, GitLab, Azure DevOps, AWS CodeBuild/CodePipeline, Jenkins, Argo CD) and **architecture and system-design packs** for every capstone (solution, logical, technical, infrastructure and cloud diagrams in Mermaid, draw.io, SVG and PNG, plus ADRs).

## How to study

Start at [Phase 1](phases/01-foundations/README.md). For each topic: read and code along, watch the videos, take the CBT, do the practice tasks. Each module has a project; from week 5 you build capstones. On Moodle the same content appears as pages and graded quizzes, with progress tracking and certificates.

## Repository layout

```text
curriculum.json                 phases, modules and topics (source of truth for structure)
phases/01-foundations/          Phase 1: modules, topics (lesson.md + cbt.json), capstones, deploy, exam, getting-hired
phases/02-..., phases/03-...    planned phases (topic lists)
templates/                      Spring Boot 4 service starter and Java CLI starter (built and tested)
labs/                           hands-on labs referenced by module projects
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
python scripts/check_repo.py         # everything consistent? (CI runs this)
python lms/check_videos.py --quick   # are all videos still online and embeddable?
python scripts/moodle/build_moodle.py   # Moodle import files (needs pandoc)
```

Generated files carry a banner saying so; edit their sources instead.

## Licence and attribution

Course content is © ITLearning. Datasets keep their own licences, listed in [docs/data-sources.md](docs/data-sources.md). Videos are linked and embedded from YouTube and belong to their creators.
