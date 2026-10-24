## What this module gives you

Two weeks on how code gets from your laptop to production in a professional team: Git branching, pull requests, rebasing and branch protection (week 21), small, secure, multi-stage Docker images and Docker Compose stacks (week 21), CI/CD with GitHub Actions and GitLab CI including build, test, scan and publish stages (week 22), the Jenkins and Azure DevOps pipelines that many banks still run (week 22), and deploying containers to a VPS and the major clouds with safe rollbacks (week 22).

This module extends the six-platform CI/CD material from Phase 1 with **quality gates**: no merge to `main` unless tests, coverage, static analysis, dependency, secret and image scans all pass.

**Exit check for Module 8:** all five topic CBTs passed; projects 8.B and 8.C completed; 8.A with a peer if you can find one (the Moodle forum pairs learners).

## Module projects

### Project 8.A: Team workflow with peer pull-request reviews

- Pair with another learner. Protect `main` (required reviews, required status checks, linear history, no force pushes) and add a `CODEOWNERS` file and a pull-request template.
- Each of you delivers three features to the other's capstone through pull requests: conventional commits, a description with test evidence, at least one requested change addressed.
- Resolve at least one real merge conflict with a rebase, and write up what happened in `CONTRIBUTING.md`.
- Run it as a **two-week mini-sprint** (topic 8.6): a shared board (GitHub Projects, Jira or Trello) with at least six tickets written as user stories with acceptance criteria, estimates from planning poker, short async stand-up notes, branches and PRs named after ticket keys, a recorded demo and a one-page retrospective. Submit the board link and the retrospective with the project.

### Project 8.B: Dockerised Spring Boot and PostgreSQL stack

- A multi-stage Dockerfile using Spring Boot's layered jar (or Cloud Native Buildpacks), running as a non-root user on a slim JRE base, with a health check.
- A Compose file for the app, PostgreSQL, Redis and Keycloak with named volumes, health-based `depends_on`, and secrets from an `.env` file that is git-ignored.
- **Acceptance:** image under 250 MB, no critical vulnerabilities in a Trivy scan, and `docker compose up` gives a working stack on a fresh machine.

### Project 8.C: GitHub Actions pipeline with quality gates

- Jobs: build and unit tests; integration tests (Testcontainers); JaCoCo coverage gate; SonarQube or CodeQL analysis; OWASP Dependency-Check or `dependency-review-action`; Gitleaks secret scan; image build, Trivy scan and push to GHCR tagged by commit SHA; deploy to a staging VPS on `main` with a manual approval for production (GitHub Environments).
- Mirror the pipeline in GitLab CI (`.gitlab-ci.yml`) with the same stages.
- **Acceptance:** a pull request that introduces a failing test, a hard-coded secret or a vulnerable dependency is blocked; screenshots of each in the README.

### Lab 8.D: Repair a container and a pipeline

A broken-service lab: [Repair a container and a pipeline](../../../labs/08-broken-pipeline-and-container/README.md), a root-running image, an exposed database and a pipeline that leaks a secret, scored by a 22-rule checker. Do it alongside the module; it counts as practice, not as the module project, but mentors review it if you submit it with your project.

## How these projects are assessed

| Criterion | Not yet | Competent | Strong |
| --- | --- | --- | --- |
| Git workflow | Commits straight to `main` | Feature branches, protected `main`, reviewed pull requests | Clean history, conventional commits, CODEOWNERS, conflict resolution documented |
| Container images | Fat image running as root | Multi-stage, non-root, health check, small base | Layered or buildpack images, reproducible builds, scanned with no criticals |
| Pipeline | Build only | Build, test, coverage, image publish | Full quality gates (static, dependency, secret, image scans) block merges |
| Deployment | Manual copy to a server | Automated deploy of an immutable image tag | Environments with approval, health-checked rollout and documented rollback |
| Documentation | None | README explains how to run and deploy | Runbook with rollback steps and pipeline diagram |
