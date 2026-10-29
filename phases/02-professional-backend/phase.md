From "I can build a Spring Boot service" to "I can be trusted with a production backend in a team": API design and third-party integration, security, a real testing strategy, Git, Docker and CI/CD pipelines, and working with AI coding assistants without losing your judgement. Every example is a payments, banking or fintech scenario, because those integrations (payment gateways, open banking, identity, KYC, card vaults) are exactly what Lagos fintechs, banks and global consultancies hire junior and mid-level Java engineers to build. The phase ends with your junior portfolio and interview preparation.

> **Prerequisite:** Phase 1 (or equivalent): Java 21, SQL, and building a Spring Boot 4 service with JPA, validation, Problem Details and Actuator. Phase 2 builds directly on the Phase 1 starter and capstones.

## How to study this phase

Each topic page has the same three parts as Phase 1, in this order:

1. **Read** the lesson and type out the code yourself.
2. **Watch** the curated videos (checked: real, embeddable, English with captions).
3. **Check your understanding** with the scenario CBT. On Moodle it is graded (pass mark 70%, unlimited attempts).

Then do the topic's **practice** tasks and, each module, at least one **module project** and its **broken-service lab**. From week 16, start your **Phase 2 capstones**; every capstone is a team-ready service with security, a full test pyramid and a CI/CD pipeline with quality gates. Keep up the weekly [coding-interview practice track](../../practice/README.md) (about 3 hours a week).

### How much time this phase takes

Be realistic: Phase 2 is the most demanding phase, because it turns you into someone a team can hand real tickets to. Estimated effort for a learner who finished Phase 1:

| Work | Hours |
| --- | --- |
| 27 topics (reading, videos, CBT, practice tasks) | 70–80 |
| 5 module projects | 35–45 |
| 5 broken-service labs | 15–20 |
| 2 capstones (Capstone 1 plus one other) | 80–100 |
| Practice track (DSA and SQL, 3 h/week) | 30–35 |
| Getting hired, mock interviews, defence and live coding | 15–20 |
| **Total** | **about 245–300** |

That is **22–27 hours a week over the 11 scheduled weeks**. If you can give 12–15 hours a week, plan for **18–20 weeks** instead and tell your mentor; the order of the work stays the same, and nothing in the certificate depends on finishing in 11 weeks. To keep the load manageable, a module project may extend one of your capstones (for example Project 6.C's admin/customer roles inside Capstone 1), and the second capstone can be your strongest stretch, not a third. A third capstone is welcome but optional.

## Weekly plan

| Week | Module | Topics | Milestone |
| --- | --- | --- | --- |
| 15 | 5 API Design & Integration | 5.1 Resource design and HTTP, 5.2 OpenAPI and versioning | OpenAPI contract for your first capstone |
| 16 | 5 | 5.3 Problem Details, pagination, idempotency, 5.4 Consuming APIs and resilience | Start Capstone 1 (payment gateway hub) |
| 17 | 5 | 5.5 GraphQL and gRPC, 5.6 System design basics | Module 5 project, Lab 5.E |
| 18 | 6 Security Essentials | 6.1 Spring Security filter chain, 6.2 JWT, OAuth2 and OIDC | Keycloak running locally |
| 19 | 6 | 6.3 RBAC and method security, 6.4 OWASP Top 10, 6.5 Passwords, secrets, dependency scanning | Secured capstone API; Module 6 project, Lab 6.D |
| 20 | 7 Testing & Code Quality | 7.1 JUnit, Mockito and TDD, 7.2 Testcontainers | |
| 21 | 7 | 7.3 API tests, 7.4 JaCoCo and SonarQube, 7.5 Code review | Quality gate green on every capstone; Lab 7.D |
| 22 | 8 Git, Docker & CI/CD | 8.1 Git workflow, 8.2 Docker images and Compose | Team PR workflow with a peer |
| 23 | 8 | 8.3 GitHub Actions and GitLab CI, 8.4 Jenkins and Azure DevOps, 8.5 Deploying containers, 8.6 Working in an agile team | Capstones deployed from CI; Module 8 mini-sprint; Lab 8.D |
| 24 | 9 AI-Assisted Engineering | 9.1 AI coding assistants, 9.2 Reviewing AI code, 9.3 AI for tests, docs and refactors | Lab 9.D |
| 25 | 9 Junior Portfolio | 9.4 CV, GitHub and LinkedIn, 9.5 Junior interview prep | **End-of-phase exam**, **capstone defence**, **live-coding assessment**, **Getting Hired**, junior portfolio review |

## Modules

| Module | Weeks | What you build |
| --- | --- | --- |
| [5. API Design & Integration](05-api-design-integration/README.md) | 15–17 | Payment gateway integration with webhooks, FX/weather consumer, news aggregator, gRPC lookup service |
| [6. Security Essentials](06-security-essentials/README.md) | 18–19 | JWT API with refresh tokens, Keycloak login, role-based admin vs customer endpoints |
| [7. Testing & Code Quality](07-testing-code-quality/README.md) | 20–21 | Full test suite for the wallet, TDD fee-engine kata, e-commerce integration tests |
| [8. Git, Docker & CI/CD](08-git-docker-cicd/README.md) | 22–23 | Team PR workflow, Dockerised stack, GitHub Actions pipeline with quality gates |
| [9. AI-Assisted Engineering & Junior Portfolio](09-ai-assisted-engineering-portfolio/README.md) | 24–25 | Polished 4–5 project portfolio, mock interview, tailored CV and application plan |

## Capstones

Ten integration- and security-heavy fintech capstones, each built on public data or official sandboxes that are free for commercial and non-commercial use, with recorded licences. **Complete at least two** by the end of the phase: Capstone 1 (the payment gateway hub) plus any one other, and defend them in a 20-minute [capstone defence](../../docs/assessments/capstone-defence.md). Capstones 1 and 2 have complete, tested reference solutions, which your mentor shares once your submission has passed.

→ [Phase 2 capstones](capstones/README.md)

## Deployment and CI/CD

Phase 2 capstones use the same six-platform pipeline as Phase 1 (GitHub Actions, GitLab CI, Azure DevOps, AWS CodeBuild/CodePipeline, Jenkins, Argo CD) with added **quality gates**: test coverage thresholds, static analysis, dependency and secret scanning, and image scanning. Deployment targets are the same: a VPS (for example SSDNodes), AWS, Azure and GCP.

→ [Deployment guide](../01-foundations/deploy/README.md) · [CI/CD guide](../01-foundations/cicd/README.md) · [Module 8](08-git-docker-cicd/README.md) adds the quality gates

## Certifications

Oracle's Java SE exams are fully covered by Phase 1. Phase 2 prepares the ground for cloud and security credentials: Module 8 starts the AWS material used by **AWS Certified Developer – Associate**, and Module 6 covers the OWASP and OAuth2 knowledge assumed by vendor security courses. See the [certification mapping](../../docs/certifications.md).

## End-of-phase assessments

- **Exam:** a 2-hour, 60-question scenario-based CBT covering every Phase 2 topic, taken on Moodle in week 25 (questions drawn at random from a larger bank). Pass mark 70%.
- **Capstone defence:** a 20-minute recorded viva on your capstones, including a live change ([format and rubric](../../docs/assessments/capstone-defence.md)).
- **Live-coding assessment:** 90 minutes, one algorithm problem and one secured or integration change to an unfamiliar service ([format and rubric](../../docs/assessments/live-coding.md)).
- **Coding practice checkpoint:** at least 70% of the [practice track](../../practice/README.md) Java and SQL problems passing.

→ [Exam blueprint](exam/README.md)

## Getting hired

Week 25 is your junior-role launch: a portfolio of four to five deployed projects, a CV and LinkedIn tuned for Java roles, and mock interviews.

→ [Getting hired after Phase 2](getting-hired/README.md)

## Phase 2 exit criteria

- All 27 topic CBTs attempted (aim for 70%+).
- At least one project per module.
- Capstone 1 plus one other passed against the rubric, each with a green pipeline that enforces quality gates, deployed and secured, and both defended in the capstone defence.
- Live-coding assessment and coding practice checkpoint passed.
- End-of-phase exam passed (70%+).
- Getting Hired checklist complete.
