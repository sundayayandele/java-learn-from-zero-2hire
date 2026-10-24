## What you will be able to do

- Explain **continuous integration**, **continuous delivery** and **continuous deployment**, and the stages of a pipeline.
- Write **GitHub Actions** workflows: triggers, jobs, steps, caching, matrices, artifacts, services, environments and secrets.
- Build a pipeline with **quality gates**: tests, coverage, static analysis, dependency, secret and image scans, then publish an image to **GHCR**.
- Authenticate to clouds from CI with **OIDC** instead of stored keys.
- Write the equivalent **GitLab CI** pipeline.
- Keep pipelines fast and secure.

## Why this matters in a bank or fintech

A pipeline is the bank's automated change-control process: it proves each change compiles, passes tests and security checks, was reviewed, and produced a known, scanned artifact, before anything reaches production. Regulators and auditors increasingly accept pipeline evidence instead of manual sign-off spreadsheets, but only if the pipeline cannot be bypassed. Platform and DevOps engineers are among the most sought-after hires in Nigerian fintech, and every backend engineer is expected to read and maintain their service's pipeline.

## 1. CI, CD and CD

- **Continuous integration (CI):** every change is merged to the trunk frequently and automatically built and tested.
- **Continuous delivery:** every change that passes the pipeline *could* be released at the push of a button; production deployment is a business decision.
- **Continuous deployment:** every passing change *is* deployed to production automatically.

Banks commonly practise continuous delivery to staging with a manual approval (or a change ticket) for production.

A pipeline for a Spring Boot service, fastest checks first:

```
pull request:  compile → unit tests → integration tests → coverage gate → static analysis gate
               → dependency review → secret scan
main branch:   all of the above → build image → scan image → push image (tag = commit SHA)
               → deploy to staging → smoke tests → [approval] → deploy to production → smoke tests
```

## 2. GitHub Actions basics

Workflows are YAML files in `.github/workflows/`. A workflow has **triggers** (`on`), **jobs** (run in parallel unless they `needs:` each other) and **steps** (shell commands or reusable **actions**).

```yaml
name: ci
on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read            # least privilege for GITHUB_TOKEN by default

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true  # a new push cancels the previous run on the same branch

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'
          cache: maven          # caches ~/.m2 keyed on pom.xml
      - name: Build, unit and integration tests, coverage gate
        run: ./mvnw -B verify   # Surefire, Failsafe (Testcontainers) and JaCoCo check
      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            target/surefire-reports
            target/failsafe-reports
```

Key concepts:

- **Runners:** GitHub-hosted (`ubuntu-latest` has Docker, so Testcontainers works) or self-hosted (inside a bank's network).
- **Caching** dependencies keeps builds fast; `setup-java` does it for Maven.
- **Artifacts** pass files between jobs or keep reports for download.
- **Matrix builds** run a job across combinations (for example Java 21 and 25) with `strategy.matrix`.
- **Expressions and contexts:** `${{ github.sha }}`, `${{ secrets.NAME }}`, `if: github.ref == 'refs/heads/main'`.
- **Reusable workflows** (`workflow_call`) let a platform team define the standard pipeline once for every service.

## 3. Quality gates as jobs

```yaml
  quality:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
        with: { fetch-depth: 0 }                        # Sonar and Gitleaks need full history
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '21', cache: maven }
      - name: SonarQube analysis with quality gate
        run: ./mvnw -B verify sonar:sonar -Dsonar.qualitygate.wait=true
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ vars.SONAR_HOST_URL }}
      - name: Secret scan
        uses: gitleaks/gitleaks-action@v2
        env: { GITHUB_TOKEN: "${{ secrets.GITHUB_TOKEN }}" }

  dependency-review:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/dependency-review-action@v5
        with: { fail-on-severity: high }
```

Alternatively (or additionally) run **CodeQL** (`github/codeql-action`) for security analysis, free for public repositories. OWASP Dependency-Check (topic 6.5) can run as a Maven step too.

Then make these jobs **required status checks** in branch protection (topic 8.1). A gate that does not block merges is only a report.

## 4. Build, scan and publish the image

```yaml
  image:
    needs: [build, quality]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write           # push to GHCR with GITHUB_TOKEN
    steps:
      - uses: actions/checkout@v5
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          load: true
          tags: ghcr.io/${{ github.repository }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
      - name: Scan image
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: ghcr.io/${{ github.repository }}:${{ github.sha }}
          severity: CRITICAL,HIGH
          ignore-unfixed: true
          exit-code: '1'
      - run: docker push ghcr.io/${{ github.repository }}:${{ github.sha }}
```

The image is tagged with the commit SHA, scanned **before** it is pushed, and the same immutable image is promoted through environments.

## 5. Deploying with environments, approvals and OIDC

**Environments** (`staging`, `production`) hold their own secrets and protection rules (required reviewers, wait timers, allowed branches):

```yaml
  deploy-production:
    needs: deploy-staging
    runs-on: ubuntu-latest
    environment: production        # pauses for an approval configured on the environment
    permissions:
      id-token: write              # allow the job to request an OIDC token
      contents: read
    steps:
      - uses: aws-actions/configure-aws-credentials@v6
        with:
          role-to-assume: arn:aws:iam::123456789012:role/github-deploy-wallet
          aws-region: eu-west-1
      - run: ./deploy/ecs-deploy.sh ghcr.io/${{ github.repository }}:${{ github.sha }}
```

**OIDC federation:** instead of storing long-lived cloud keys as secrets, the job requests a short-lived token from GitHub, and the cloud (AWS, Azure, GCP) trusts it for a specific repository, branch and environment. No key to leak or rotate. Use it for every cloud deployment.

For a VPS, the deploy step SSHes to the server and runs `docker compose pull && docker compose up -d` with the new tag (the Phase 1 deployment guide shows the full script).

## 6. Pipeline security

The pipeline has the keys to production, so attackers target it:

- Set `permissions:` explicitly and minimally; default `GITHUB_TOKEN` to read-only.
- **Pin third-party actions** to a full commit SHA (`uses: some/action@8f4b7f8...`) or at least a major version you trust; Dependabot can update them.
- Do not run untrusted pull-request code with access to secrets (`pull_request_target` with a checkout of the PR head is a classic mistake).
- Never `echo` secrets; GitHub masks known secrets, but derived values are not masked.
- Protect workflow files with CODEOWNERS so changes to the pipeline need platform-team review.
- Keep deployment credentials in environments that only `main` can use.

## 7. The same pipeline in GitLab CI

```yaml
# .gitlab-ci.yml
stages: [build, test, quality, package, deploy]

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

default:
  image: eclipse-temurin:21-jdk
  cache:
    key: { files: [pom.xml] }
    paths: [.m2/repository]

verify:
  stage: test
  services: [docker:27-dind]               # Testcontainers needs a Docker daemon
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
    TESTCONTAINERS_HOST_OVERRIDE: docker
  script: ./mvnw -B verify
  artifacts:
    when: always
    reports:
      junit: [target/surefire-reports/TEST-*.xml, target/failsafe-reports/TEST-*.xml]

secret-scan:
  stage: quality
  image: { name: zricethezav/gitleaks:latest, entrypoint: [""] }
  script: gitleaks detect --source . --verbose

build-image:
  stage: package
  image: docker:27
  services: [docker:27-dind]
  rules: [{ if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH }]
  script:
    - docker login -u "$CI_REGISTRY_USER" -p "$CI_REGISTRY_PASSWORD" "$CI_REGISTRY"
    - docker build -t "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA" .
    - docker push "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"

deploy-production:
  stage: deploy
  environment: production
  when: manual                              # approval
  rules: [{ if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH }]
  script: ./deploy/vps-deploy.sh "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"
```

The concepts map one to one: stages and jobs, `services` (like GitHub's service containers), `rules` (like `if:`), protected environments and `when: manual` (approvals), CI/CD variables (secrets). GitLab also offers built-in SAST, dependency and container scanning templates. (Note that the job above is named `build-image`: `image` is a reserved keyword in GitLab CI and cannot be a job name.)

## 8. Keeping pipelines fast

- Cache dependencies; cache Docker layers (`type=gha` cache in Buildx).
- Run independent jobs in parallel; fail fast on cheap checks.
- Keep unit tests fast (topic 7.1) and integration tests focused (topic 7.2).
- Cancel superseded runs with `concurrency`.
- Measure: a pull-request pipeline over 10–15 minutes changes developer behaviour for the worse.

## Common mistakes

- A pipeline that runs tests but does not block merges.
- Long-lived cloud access keys stored as CI secrets instead of OIDC.
- `permissions: write-all` and unpinned third-party actions.
- Building a different image for each environment instead of promoting one image.
- Deploying `latest`; no record of which commit is in production.
- Secrets printed in logs; `pull_request_target` with untrusted code.
- 40-minute pipelines nobody waits for.

## Practice

1. Build the full GitHub Actions pipeline from sections 2–5 for your capstone (project 8.C), with every gate as a required check.
2. Configure `staging` and `production` environments with a required reviewer, and OIDC to AWS or Azure (or SSH to a VPS with a deploy key stored only in the environment).
3. Open three pull requests that each break one gate (a failing test, a fake AWS key, a dependency with a known CVE) and screenshot the blocked merges.
4. Mirror the repository to gitlab.com and get the GitLab pipeline green.

## Key terms

Continuous integration, continuous delivery, continuous deployment, pipeline, stage, job, step, workflow, trigger, runner, action, cache, artifact, matrix, reusable workflow, quality gate, required status check, GHCR, image tag, promotion, environment, protection rule, approval, OIDC federation, least-privilege token, action pinning, `pull_request_target`, GitLab CI, `.gitlab-ci.yml`, service container, Docker-in-Docker, `rules`, `when: manual`.

## Further reading

- [GitHub Docs: Understanding GitHub Actions](https://docs.github.com/en/actions/get-started/understand-github-actions)
- [GitHub Docs: Building and testing Java with Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
- [GitHub Docs: Security hardening for GitHub Actions](https://docs.github.com/en/actions/reference/security/secure-use)
- [GitHub Docs: OpenID Connect in cloud providers](https://docs.github.com/en/actions/concepts/security/openid-connect)
- [GitLab Docs: CI/CD YAML syntax reference](https://docs.gitlab.com/ci/yaml/)
- [Testcontainers: Patterns for running tests inside a Docker container (GitLab CI)](https://java.testcontainers.org/supported_docker_environment/continuous_integration/gitlab_ci/)
