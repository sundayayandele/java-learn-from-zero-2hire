# Phase 1 end-of-phase exam

← [Phase 1](../README.md)

| | |
| --- | --- |
| When | Week 13, after all 26 topic CBTs are passed |
| Where | Moodle (itlearning.tech), "Phase 1 end-of-phase exam" quiz |
| Format | **56 scenario-based multiple-choice questions**, each a short workplace situation with one best answer |
| Time | **2 hours** (120 minutes), one sitting; the timer keeps running if you close the browser |
| Pass mark | **70%** |
| Attempts | 2, at least 48 hours apart; highest grade counts |
| Order | Questions and answer options are shuffled for every attempt |
| Resources | Closed book. No IDE, no AI assistants, no notes |
| Review | Correct answers and explanations are shown after the exam closes for the cohort |

## Blueprint

The exam samples every Phase 1 topic, weighted by how much each module matters in junior interviews and on the job.

| Module | Questions | Weight | Topics |
| --- | --- | --- | --- |
| 1. Modern Java & OOP | 18 | 32% | Tooling and Linux (1.1), syntax and money types (1.2), OOP/SOLID (1.3), collections and generics (1.4), lambdas and streams (1.5), records/sealed/pattern matching (1.6), exceptions and immutability (1.7), legacy migration (1.8), Kotlin (1.9) |
| 2. Concurrency & JVM | 9 | 16% | Executors and CompletableFuture (2.1), races, locks, atomics, deadlocks (2.2), virtual threads (2.3), memory and GC (2.4) |
| 3. Databases | 15 | 27% | Design and joins (3.1), transactions and locking (3.2), indexing and plans (3.3), migrations (3.4), JDBC and SQL injection (3.5), Redis and MongoDB (3.6) |
| 4. Spring Boot 4 | 14 | 25% | DI and auto-configuration (4.1), MVC, validation, Problem Details (4.2), JPA and N+1 (4.3), configuration and secrets (4.4), Actuator (4.5), Boot 3 → 4 (4.6), Kotlin with Spring (4.7) |
| **Total** | **56** | **100%** | |

## What the questions look like

Every question describes a realistic situation, usually from a bank or fintech, and asks for the best action, diagnosis or design. Two examples in the exam's style (these two are not in the exam):

**Example 1.** A nightly job reads 4 million transactions and checks each reference against a `List<String>` of already-processed references. It takes six hours. What single change helps most?

- A. Sort the list before checking
- B. Use a `HashSet<String>` for processed references
- C. Run the job on a server with more RAM
- D. Use a `LinkedList`

<details><summary>Answer</summary>

**B.** `List.contains` is O(n), so the job is O(n²). A `HashSet` makes each check O(1) on average.

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

1. Retake every topic CBT without notes until you score 90%+. They use the same style.
2. Re-read the "Common mistakes" section of each lesson: many exam scenarios are built on them.
3. Explain your Capstone 1 design aloud: locking, idempotency, schema constraints. Many questions test the same reasoning.
4. Practise under time pressure: about 2 minutes per question.

## For instructors

The question bank (with answers and explanations) is **not** stored in this public repository. It lives in `assessments/private/` (git-ignored) and is converted to Moodle XML by `scripts/moodle/build_moodle.py`. Keep it in a private repository or the LMS only.
