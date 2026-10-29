# Phase 2 end-of-phase exam

← [Phase 2](../README.md)

| | |
| --- | --- |
| When | Week 25, after all 27 Phase 2 topic CBTs are attempted |
| Where | Moodle (itlearning.tech), "Phase 2 end-of-phase exam" quiz |
| Format | **60 scenario-based multiple-choice questions**, each a short workplace situation with one best answer, drawn at random for every attempt from a bank of 200 (the module weights below are fixed) |
| Time | **2 hours** (120 minutes), one sitting; the timer keeps running if you close the browser |
| Pass mark | **70%** |
| Attempts | 2, at least 48 hours apart; highest grade counts |
| Order | Questions and answer options are shuffled for every attempt |
| Resources | Closed book. No IDE, no AI assistants, no notes |
| Review | Correct answers and explanations are shown after the exam closes for the cohort |

## Blueprint

| Module | Questions | Weight | Topics |
| --- | --- | --- | --- |
| 5. API Design & Integration | 15 | 25% | Resource design and HTTP semantics (5.1), OpenAPI and versioning (5.2), Problem Details, pagination and idempotency (5.3), consuming APIs, resilience and webhooks (5.4), GraphQL and gRPC (5.5), system design basics (5.6) |
| 6. Security Essentials | 13 | 22% | Filter chain (6.1), JWT, OAuth2 and OIDC (6.2), RBAC, method security and object-level authorisation (6.3), OWASP Top 10 for Java (6.4), passwords, secrets and dependency scanning (6.5) |
| 7. Testing & Code Quality | 12 | 20% | JUnit, Mockito and TDD (7.1), Testcontainers and WireMock (7.2), API tests (7.3), coverage, mutation testing and quality gates (7.4), code review (7.5) |
| 8. Git, Docker & CI/CD | 11 | 18% | Git workflow (8.1), Docker images and Compose (8.2), GitHub Actions and GitLab CI (8.3), Jenkins and Azure DevOps (8.4), deploying containers safely (8.5), working in an agile team (8.6) |
| 9. AI-Assisted Engineering & Portfolio | 9 | 15% | AI coding assistants (9.1), reviewing AI code (9.2), AI for tests, docs and refactors (9.3), CV, GitHub and LinkedIn (9.4), interview preparation (9.5) |
| **Total** | **60** | **100%** | |

## What the questions look like

Two examples in the exam's style (these two are not in the exam):

**Example 1.** A merchant's server retries `POST /payments` after a timeout, and the customer is charged twice. What is the most robust fix on the API side?

- A. Tell merchants not to retry
- B. Require an `Idempotency-Key` header and replay the original result for repeated keys with the same payload
- C. Increase the server timeout
- D. Return 500 on every retry

<details><summary>Answer</summary>

**B.** Retries after timeouts are normal; idempotency keys make them safe, and the key must be claimed atomically so concurrent duplicates are handled too.

</details>

**Example 2.** An API test passes a JWT with `ROLE_CUSTOMER` and gets 200 for `GET /cards/{id}` with another customer's card id. What kind of defect is this?

- A. Broken authentication
- B. Broken object level authorisation
- C. Cross-site request forgery
- D. Security logging failure

<details><summary>Answer</summary>

**B.** The caller is authenticated and has the right role, but the service never checks that the card belongs to them.

</details>

## How to prepare

1. Retake all 27 Phase 2 topic CBTs without notes until you score 90%+.
2. Re-read each lesson's "Common mistakes" section.
3. Explain your Capstone 1 aloud: idempotency, webhooks, resilience, security model and test strategy.
4. Practise under time pressure: about 2 minutes per question.

## For instructors

The question bank (with answers and explanations) is **not** stored in this public repository. It lives in the private repository `sundayayandele/java-zero-hire-assessments` (access on request) as `phase-2-exam.json`. Clone it into `assessments/private/` (git-ignored here) and run `scripts/moodle/build_moodle.py` to produce the Moodle XML.
