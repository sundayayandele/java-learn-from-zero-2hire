# Phase 1 end-of-phase exam

← [Phase 1](../README.md)

| Item | Details |
| --- | --- |
| When | Week 14, after all 31 core topic CBTs are attempted |
| Where | Moodle (itlearning.tech), "Phase 1 end-of-phase exam" quiz |
| Format | **60 scenario-based multiple-choice questions**, each a short workplace situation with one best answer, drawn at random for every attempt from a bank of 200 (the module weights below are fixed) |
| Time | **2 hours** (120 minutes), one sitting; the timer keeps running if you close the browser |
| Pass mark | **70%** |
| Attempts | 2, at least 48 hours apart; highest grade counts |
| Order | Questions and answer options are shuffled for every attempt |
| Resources | Closed book. No IDE, no AI assistants, no notes |
| Review | Correct answers and explanations are shown after the exam closes for the cohort |

## Blueprint

The exam samples every core Phase 1 topic, weighted by how much each module matters in junior interviews and on the job.

| Module | Questions | Weight | Topics |
| --- | --- | --- | --- |
| 1. Modern Java & OOP | 20 | 33% | Tooling and Linux (1.1), Git and GitHub (1.2), syntax and money types (1.3), OOP/SOLID (1.4), collections and generics (1.5), unit testing with JUnit (1.6), lambdas and streams (1.7), records/sealed/pattern matching (1.8), exceptions and immutability (1.9), I/O and NIO.2 (1.10), design patterns (1.11), legacy migration (1.12) |
| 2. Concurrency & JVM | 12 | 20% | Executors and CompletableFuture (2.1), races, locks, atomics, deadlocks (2.2), virtual threads (2.3), memory and GC (2.4), debugging and troubleshooting (2.6) |
| 3. Databases | 14 | 23% | Design and joins (3.1), transactions and locking (3.2), indexing and plans (3.3), migrations (3.4), JDBC and SQL injection (3.5), Redis and MongoDB (3.6) |
| 4. Spring Boot 4 | 14 | 23% | DI and auto-configuration (4.1), HTTP and REST basics (4.2), MVC, validation, Problem Details (4.3), JPA and N+1 (4.4), configuration and secrets (4.5), Actuator (4.6), Boot 3 → 4 (4.7), Spring under the hood (4.8) |
| **Total** | **60** | **100%** | |

The optional topics (1.13 Kotlin, 1.14 Localization, 1.15 Java 22 to 25, 2.5 Modules and jlink, 4.9 Spring Boot with Kotlin) and the week-0 on-ramp aren't examined.

## What the questions look like

Every question describes a realistic situation, usually from a bank or fintech, and asks for the best action, diagnosis or design. Two examples in the exam's style (these two are not in the exam):

**Example 1.** A nightly job reads 4 million transactions and checks each reference against a `List<String>` of already-processed references. It takes six hours. What single change helps most?

- A. Sort the list before checking
- B. Use a `HashSet<String>` for processed references
- C. Run the job on a server with more RAM
- D. Use a `LinkedList`

<details><summary>Answer</summary>

**B.** `List.contains` is O(*n*), so the job is O(*n*²). A `HashSet` makes each check O(1) on average.

</details>

**Example 2.** After upgrading to Spring Boot 4, a service starts normally, but its new database tables never appear. The POM contains `flyway-core` and no Flyway starter. What's wrong?

- A. Flyway isn't supported in Spring Boot 4
- B. The migration files must be renamed for Boot 4
- C. Boot 4's modular auto-configuration needs `spring-boot-starter-flyway`
- D. `ddl-auto` must be set to `update`

<details><summary>Answer</summary>

**C.** In Boot 4, technologies that used to activate from the bare library need their starter, otherwise migrations silently don't run.

</details>

## How to prepare

1. Retake all 31 core topic CBTs without notes until you score 90%+. They use the same style.
2. Re-read the "Common mistakes" section of each lesson: many exam scenarios are built on them.
3. Explain your Capstone 1 design aloud: locking, idempotency, schema constraints. Many questions test the same reasoning.
4. Practise under time pressure: about 2 minutes per question.

## For instructors

The question bank (with answers and explanations) is **not** stored in this public repository. It lives in the private repository `sundayayandele/java-zero-hire-assessments` (access on request). Clone it into `assessments/private/` (git-ignored here) and run `scripts/moodle/build_moodle.py` to produce the Moodle XML.
