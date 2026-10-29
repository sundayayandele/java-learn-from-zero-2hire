## What you will be able to do

- Describe the typical **interview loop** for junior Java backend roles in banks, fintechs and consultancies.
- Answer the most common **core Java**, **Spring**, **SQL** and **HTTP/API** questions clearly, with examples from your own projects.
- Approach **live-coding** problems with a repeatable method: clarify, plan, code, test, discuss complexity.
- Walk an interviewer through **one of your capstones** as a small system-design discussion.
- Prepare **behavioural** answers with the STAR method and ask good questions at the end.

## Why this matters in a bank or fintech

Interviews test whether you can do the job *and* whether the team would trust you with production money movement. Candidates who have built real projects often fail interviews because they cannot explain their own decisions under pressure, or freeze in live coding without an AI assistant. Structured preparation fixes both. This lesson pulls together everything from Phases 1 and 2 into interview form.

## 1. The typical loop

| Stage | Length | What they check |
| --- | --- | --- |
| Recruiter screen | 15–30 min | Motivation, communication, salary expectations, right to work, notice period |
| Online assessment (some employers) | 60–90 min | Coding problems (HackerRank, Codility) or a short take-home |
| Technical interview | 45–60 min | Core Java, Spring, SQL, APIs, testing; questions about your projects |
| Live coding | 30–60 min | Problem solving, code quality, testing, talking through your thinking |
| Design / project deep-dive | 45–60 min | One of your projects or a small system (wallet, payment API) |
| Behavioural / hiring manager | 30–45 min | Teamwork, ownership, learning, handling mistakes, values |

Banks may add aptitude tests, background checks and a panel interview; fintechs often have a take-home exercise. Ask the recruiter what each stage involves; it is a normal question.

## 2. Core Java: questions you should answer cold

Practise saying these answers aloud, each in under two minutes, with a code example or a project reference.

1. **`equals` and `hashCode`**: the contract, why both must be overridden together, and what happens in a `HashMap` when they are not.
2. **`String` immutability** and the string pool; why `StringBuilder` in loops.
3. **`BigDecimal` for money**: why not `double`; `setScale` with `RoundingMode.HALF_EVEN`; `compareTo` versus `equals` (Phase 1 topic 1.3).
4. **Collections**: `ArrayList` vs `LinkedList`; `HashMap` internals (buckets, hashing, resize, tree bins); `HashMap` vs `ConcurrentHashMap`; iteration order guarantees.
5. **Generics**: type erasure; `List<? extends Number>` vs `List<? super Integer>` (PECS).
6. **Streams**: lazy evaluation; `map` vs `flatMap`; `groupingBy` with downstream collectors; when a loop is clearer.
7. **Records, sealed types, pattern matching**: what they replace and why they help domain modelling.
8. **Exceptions**: checked vs unchecked; custom domain exceptions; try-with-resources.
9. **Concurrency**: race conditions; `synchronized` vs `ReentrantLock` vs atomics; deadlocks and lock ordering; `ExecutorService`; `CompletableFuture`; **virtual threads** and when they help (I/O-bound) or not (CPU-bound, pinning).
10. **JVM memory**: heap vs stack; garbage collection basics; `OutOfMemoryError` causes; container memory sizing.

## 3. Spring and Spring Boot

1. **Dependency injection and IoC**: why constructor injection; bean scopes; how auto-configuration works (conditions on the classpath and properties).
2. **The request path**: `DispatcherServlet` → filters (security) → handler mapping → controller → message converters → advice for errors.
3. **`@Transactional`**: how the proxy works; propagation (`REQUIRED`, `REQUIRES_NEW`); why self-invocation and private methods are not transactional; rollback rules for checked exceptions.
4. **JPA**: entity lifecycle; lazy loading and `LazyInitializationException`; the N+1 problem and how to fix it (fetch joins, entity graphs, projections); optimistic locking with `@Version`.
5. **Validation and errors**: Bean Validation on DTO records; Problem Details; 400 vs 422.
6. **Configuration**: profiles; `@ConfigurationProperties`; secrets from the environment.
7. **Security**: filter chain; authentication vs authorisation; JWT resource server; object-level authorisation (topics 6.1–6.3).
8. **Testing**: `@WebMvcTest` vs `@SpringBootTest`; Testcontainers; `@MockitoBean`.
9. **Actuator**: health, readiness and liveness; metrics.
10. **What changed in Spring Boot 4** (topic 4.7) and why you would upgrade.

## 4. SQL and databases

- Write queries live: joins (inner, left), `GROUP BY` with `HAVING`, a **window function** (running balance, rank per group), a subquery or CTE. Example: "For each account, the last three transactions" (`ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY created_at DESC)`).
- Explain **indexes**: B-tree basics, composite index column order, when an index is not used, reading `EXPLAIN ANALYZE` (Phase 1 topic 3.3).
- **Transactions and isolation**: ACID; dirty/non-repeatable/phantom reads; `READ COMMITTED` vs `REPEATABLE READ` vs `SERIALIZABLE`; `SELECT ... FOR UPDATE`.
- **Normalisation** and when to denormalise; constraints that protect invariants.
- **Migrations** with Flyway and zero-downtime changes (expand and contract).

## 5. HTTP and API design

Expect "design a REST API for X" (wallet, loan application, merchant payouts). Cover: resources and URLs, methods and status codes, idempotency keys, pagination, error format, authentication, versioning, and what happens on timeouts and retries (Module 5).

## 6. Live coding: a method

1. **Clarify** inputs, outputs, edge cases and constraints before typing. Restate the problem.
2. **Give an example** and walk through it by hand.
3. **Outline** an approach (start simple; mention a better one if you see it) and its time/space complexity.
4. **Code** cleanly with meaningful names, talking as you go. Use the standard library (`HashMap`, `Deque`, streams where clear).
5. **Test** with your example and edge cases (empty input, one element, duplicates, large values, negative amounts).
6. **Discuss** improvements and trade-offs.

Common junior problem types: string and array manipulation, hash-map counting ("first non-repeating character", "two-sum"), sorting with comparators, stacks (balanced brackets), simple recursion, and *domain* problems such as:

- "Given a list of transactions, return each account's balance" (`groupingBy` + `reducing` with `BigDecimal`).
- "Detect accounts with more than three debits within 10 minutes" (sort by time, sliding window per account).
- "Merge two sorted statements" (two pointers).
- "Validate a NUBAN check digit" (arithmetic with weights).
- "Implement a simple rate limiter" (token bucket with a `Clock`).

Practise without AI assistants and with a timer. Many interviews use a plain editor without autocomplete.

## 7. Walking through your project

Prepare a 5-minute walkthrough of one capstone, then expect deep questions. Use this structure:

1. **Problem and users:** "Mobile money operators need to detect fraud in real time…"
2. **Architecture:** draw it (solution and technical views from your architecture pack): components, data stores, integrations.
3. **Key decisions and trade-offs:** "pessimistic locking because contention on popular accounts is high"; "cursor pagination because histories are large"; "Keycloak instead of home-grown tokens".
4. **How you know it works:** test pyramid, coverage and mutation score, load test numbers, CI gates.
5. **What went wrong and what you learned:** one real bug or design mistake and the fix.
6. **What you would do next:** scaling, observability, Kafka (shows you know Phase 3 topics).

Likely follow-ups: "What happens if two transfers hit the same account at the same time?", "What if the payment provider times out?", "How would this scale to 100x traffic?", "How do you deploy without downtime?", "How did you use AI tools on this, and how did you check the output?"

## 8. Behavioural questions with STAR

Answer with **Situation, Task, Action, Result**, about 2 minutes each, focusing on *your* actions. Prepare five stories from projects, previous jobs or study:

- A difficult bug you found and fixed (with how you diagnosed it).
- A time you disagreed with someone (a code review) and how it was resolved.
- A mistake you made and what you changed afterwards.
- Learning something difficult quickly.
- Delivering under a deadline or with unclear requirements.

Banks value integrity and care: stories about protecting customers' money or data, raising a risk, or following a control even when inconvenient land well.

## 9. Your questions and the offer

Ask 2–3 genuine questions: "What does a junior's first three months look like?", "How do you review code and deploy?", "What would make someone successful in this team?" For offers, know your market range (from peers and salary surveys), ask about learning budget, mentorship and on-call expectations, and get the offer in writing.

## 10. A four-week preparation plan

| Week | Focus |
| --- | --- |
| 1 | Core Java and collections: answer section 2 aloud; 5 live-coding problems |
| 2 | Spring, JPA, transactions and security: section 3; project walkthrough drafted |
| 3 | SQL and API design: 15 queries, two API designs on paper; 5 more problems |
| 4 | Two mock interviews (project 9.B); STAR stories polished; weak areas revisited |

## Common mistakes

- Memorised definitions with no examples; not knowing your own project's details.
- Starting to code before clarifying the problem; silence while coding.
- Never testing your solution or considering edge cases.
- Claiming knowledge you cannot defend.
- Blaming others in behavioural answers.
- Only practising with an AI assistant switched on.

## Practice

1. Record yourself answering ten questions from sections 2–4; watch it back and improve two answers.
2. Solve ten live-coding problems from section 6 without AI, timed (30 minutes each), and write tests for each.
3. Prepare and rehearse the 5-minute walkthrough of your best capstone, with the architecture diagram.
4. Write five STAR stories and complete the mock interview (project 9.B).

## Key terms

Interview loop, recruiter screen, online assessment, take-home, live coding, time complexity, space complexity, edge case, project deep-dive, trade-off, STAR method, behavioural interview, hiring manager, offer, salary range, mock interview.

## Further reading

- [Tech Interview Handbook: Coding interview preparation](https://www.techinterviewhandbook.org/coding-interview-prep/)
- [Baeldung: Java interview questions](https://www.baeldung.com/java-interview-questions)
- [Baeldung: Spring interview questions](https://www.baeldung.com/spring-interview-questions)
- [LeetCode: Problem set (filter by Easy)](https://leetcode.com/problemset/)
- [PostgreSQL tutorial: Window functions](https://www.postgresql.org/docs/17/tutorial-window.html)
- [Tech Interview Handbook: Behavioral interview questions](https://www.techinterviewhandbook.org/behavioral-interview-questions/)
