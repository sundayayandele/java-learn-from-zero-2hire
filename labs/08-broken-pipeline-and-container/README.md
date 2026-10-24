# Lab 8.D: Repair a container and a pipeline

← [Module 8](../../phases/02-professional-backend/08-git-docker-cicd/README.md) · [Topic 8.2](../../phases/02-professional-backend/08-git-docker-cicd/8.2-docker-multistage-compose/README.md) · [Topic 8.3](../../phases/02-professional-backend/08-git-docker-cicd/8.3-ci-cd-github-actions-gitlab/README.md)

A colleague containerised a small rates API (`app/`) and wrote its first pipeline. It "works", but the image runs as root on an unpinned `maven:latest` base with the whole build toolchain inside, the Compose file publishes the database to the world with a hard-coded password, and the workflow pushes untested images from every branch, ignores failing tests and **prints a secret to the build log**.

`python3 check_lab.py` checks 19 rules across `app/Dockerfile`, `app/.dockerignore`, `app/compose.yaml` and `app/.github/workflows/ci.yml`; today **0/19 pass**. `python3 check_lab.py --docker` adds three more by building the image and running it (Docker required).

## How to work

1. Run the checker and read every hint. Before changing anything, write down in `FINDINGS.md` what an attacker or an unlucky Monday morning could do with each problem (for example: what can anyone on the internet do with port 5432 and that password?).
2. Fix the files, using the Phase 2 starters as a reference: a multi-stage, layered, non-root Dockerfile with a health check and container-aware JVM memory; a Compose file with secrets from `.env`, a pinned database and health-gated start-up; and a workflow that tests first, publishes only from `main`, uses least-privilege permissions and current actions, and tags images with the commit SHA.
3. Keep `./mvnw verify` green inside `app/` and reach **22/22** with `--docker`.
4. In `FINDINGS.md`, add a short note on what you would do about the secret that was printed to old build logs (rotate it, then purge or restrict the logs).

No solution is published; mentors review the files and `FINDINGS.md`.
