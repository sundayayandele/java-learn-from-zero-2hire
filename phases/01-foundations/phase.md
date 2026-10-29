From zero programming experience to building your first production-style Spring Boot 4 services: modern Java 21 (with optional Kotlin), concurrency and the JVM, relational and NoSQL databases, and Spring Boot fundamentals. Every example uses banking and payments scenarios, because those are the systems Lagos fintechs, banks and global consultancies hire Java engineers to build.

> **Status:** Phases 1 and 2 are fully built. Phase 3 is mapped in the [full curriculum](../../CURRICULUM.md).

## How to study this phase

**New to programming?** Start with the [week-0 on-ramp](00-programming-on-ramp/README.md): first programs, loops, arrays, strings and methods, at a gentler pace. Take its 15-minute **entry check** first: if you score 12 out of 15 or more, skip the on-ramp and start at topic 1.1.

Each topic page has three parts, and you should do them in this order:

1. **Read** the lesson and type out the code yourself. Copy-paste teaches nothing.
2. **Watch** the curated videos (all checked: real, embeddable, English with captions). They reinforce the reading from a second voice; they don't replace it.
3. **Check your understanding** with the scenario CBT at the end of the topic. On Moodle it is the topic quiz: unlimited attempts, and aim for 70% or more. On GitHub the answers are folded under each question.

Then do the topic's **practice** tasks and, each module, at least one **module project**. From week 6, start your **capstones**. From week 3, spend about 3 hours a week on the [coding-interview practice track](../../practice/README.md): auto-graded Java and SQL problems in the format of the online assessments most employers use as their first filter.

**Optional topics.** Five topics are marked *optional*: 1.13 Kotlin, 1.14 Localization, 1.15 Java 22 to 25, 2.5 Modules and `jlink`, and 4.9 Spring Boot with Kotlin. Kotlin, localization and `jlink` matter for some employers and for the Oracle OCP exam but rarely come up in junior Java backend interviews; 1.15 prepares you for teams already on Java 25. None of them is in the exam or the certificate requirements; do them if you have time or your target employer uses them.

### How much time this phase takes

Estimated effort for a learner who finished the on-ramp, or who already knows another language:

| Work | Hours |
| --- | --- |
| 31 core topics (reading, videos, CBT, practice tasks) | 75–85 |
| 4 module projects | 30–40 |
| 3 capstones (Capstone 1 plus two others) | 75–90 |
| Practice track (DSA and SQL, 3 h a week from week 3) | 35 |
| Exam, capstone defence, live coding and getting hired | 10–15 |
| **Total** | **about 225–265** |
| Week-0 on-ramp, if you need it | 12–15 more |

That is **16–19 hours a week over the 14 scheduled weeks**. If you can give 10–12 hours a week, plan for **20–24 weeks** instead and tell your mentor; the order of the work stays the same, and nothing in the certificate depends on finishing in 14 weeks.

## Weekly plan

| Week | Module | Topics | Milestone |
| --- | --- | --- | --- |
| 0 | 0 On-ramp (optional) | Entry check; 0.1 First programs, 0.2 Loops, arrays and strings, 0.3 Methods and problem solving | On-ramp project; JDK and IntelliJ installed |
| 1 | 1 Modern Java & OOP | 1.1 Toolkit, 1.2 Git and GitHub, 1.3 Syntax and types | First Maven build pushed to GitHub with a green Actions run |
| 2 | 1 | 1.4 OOP and SOLID, 1.5 Collections and generics | |
| 3 | 1 | 1.6 Unit testing with JUnit 5, 1.7 Lambdas and streams | Practice track starts |
| 4 | 1 | 1.8 Records, sealed types, pattern matching, 1.9 Exceptions and clean code, 1.10 I/O and NIO.2 | |
| 5 | 1 | 1.11 Design patterns, 1.12 Legacy Java migration; optional 1.13 Kotlin, 1.14 Localization, 1.15 Java 22 to 25 | Module 1 project pushed with tests and green CI |
| 6 | 2 Concurrency & JVM | 2.1 Threads and executors, 2.2 Races and locks | Start Capstone 1 design |
| 7 | 2 | 2.3 Virtual threads, 2.4 JVM memory and GC, 2.6 Debugging and troubleshooting; optional 2.5 Modules and jlink | Module 2 find-and-fix lab |
| 8 | 3 Databases | 3.1 Relational design and joins, 3.2 Transactions and locking | Ledger schema (project 3.B) |
| 9 | 3 | 3.3 Indexing and plans, 3.4 Migrations, 3.5 JDBC | Query-tuning lab (project 3.D) |
| 10 | 3 | 3.6 Redis and MongoDB | Capstone 1 core logic complete |
| 11 | 4 Spring Boot 4 | 4.1 DI and auto-configuration, 4.2 HTTP and REST basics | |
| 12 | 4 | 4.3 MVC, validation, errors, 4.4 Spring Data JPA and Hibernate | Capstone 2 started |
| 13 | 4 | 4.5 Configuration and secrets, 4.6 Actuator | Capstones deployed to a VPS or cloud |
| 14 | 4 | 4.7 Boot 3 → 4 upgrade, 4.8 Spring under the hood; optional 4.9 Spring Boot with Kotlin | **End-of-phase exam**, **capstone defence**, **live-coding assessment**, **Getting Hired** session, portfolio checkpoint |

## Modules

| Module | Weeks | What you build |
| --- | --- | --- |
| [0. Programming On-Ramp](00-programming-on-ramp/README.md) (optional) | 0 | Market-stall receipt, savings goal planner or class score report |
| [1. Modern Java & OOP Foundations](01-modern-java-oop/README.md) | 1–5 | Wallet simulator, library CLI, payroll calculator or inventory manager |
| [2. Concurrency & the JVM](02-concurrency-jvm/README.md) | 6–7 | Parallel transaction processor, rate-limited caller, find-and-fix lab |
| [3. Databases: SQL & NoSQL](03-databases-sql-nosql/README.md) | 8–10 | E-commerce schema, double-entry ledger, hospital DB, query-tuning lab |
| [4. Spring Boot 4 Fundamentals](04-spring-boot-fundamentals/README.md) | 11–14 | Task manager API, blog API, product catalogue, Boot 3 → 4 upgrade |

## Capstones

Ten financial-institution capstones built on public datasets that are free for commercial and non-commercial use, each with a recorded licence. **Complete at least three** by the end of the phase: Capstone 1 (the ledger) plus any two others. Capstones 1 and 2 have complete reference solutions, which your mentor shares once your submission has passed.

→ [Phase 1 capstones](capstones/README.md)

## Deployment and CI/CD

Every capstone ships with the same pipeline on six platforms: GitHub Actions (primary), GitLab CI (secondary, free gitlab.com), Azure DevOps, AWS CodeBuild/CodePipeline, Jenkins, and Kubernetes manifests with Argo CD for GitOps, plus infrastructure sizing and step-by-step deployment to a VPS (for example SSDNodes), AWS, Azure and GCP.

→ [Deployment guide](deploy/README.md) · [CI/CD guide: GitHub Actions, GitLab, Azure DevOps, AWS, Jenkins, Argo CD](cicd/README.md)

## Oracle certification

Phase 1 Modules 1–2 cover every objective of the **Oracle Certified Professional: Java SE 21 (1Z0-830)** and **Java SE 25 (1Z0-831)** exams. See the [objective-by-objective mapping](../../docs/certifications.md) and take the OCP after week 14.

## End-of-phase assessments

- **Exam:** a 2-hour, 60-question scenario-based CBT covering every Phase 1 topic, taken on Moodle in week 14 (questions drawn at random from a larger bank). Pass mark 70%.
- **Capstone defence:** a 20-minute recorded viva on your capstones, including a live change ([format and rubric](../../docs/assessments/capstone-defence.md)).
- **Live-coding assessment:** 90 minutes, one algorithm problem and one change to an unfamiliar Spring Boot service ([format and rubric](../../docs/assessments/live-coding.md)).
- **Coding practice checkpoint:** at least 70% of the Phase 1 weeks' [practice track](../../practice/README.md) problems passing.

→ [Exam information and blueprint](exam/README.md)

## Getting hired

What a Phase 1 graduate can credibly apply for, how to present your capstones, and how to prepare for junior Java interviews.

→ [Getting Hired after Phase 1](getting-hired/README.md)

## Phase 1 exit criteria

- [ ] All 31 core topic CBTs attempted (aim for 70%+); the on-ramp and the five optional topics don't count
- [ ] One project per module, each on GitHub with README, tests and green CI
- [ ] Three capstones (including Capstone 1), at least one deployed with a public health endpoint
- [ ] Capstone defence, live-coding assessment and coding practice checkpoint passed
- [ ] End-of-phase exam passed (70%+)
- [ ] Getting Hired checklist complete: CV draft, GitHub profile README, LinkedIn updated
