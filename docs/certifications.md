# Oracle Java certifications mapped to the curriculum

How this programme prepares you for Oracle's Java certifications at each level (Associate, Professional and Expert), objective by objective, and where each phase fits.

> **Checked 23 October 2026.** Oracle University's site (education.oracle.com) was down for maintenance when this mapping was made. Exam codes, formats and objectives below come from Oracle's dev.java certification overview and the published 1Z0-831 objective list reproduced by Enthuware (both verified on the day). Before booking an exam, confirm the current details, price and retirement dates on Oracle's certification pages.

## The tracks at a glance

| Level | Credential | Exam | Format (as published) | Status | Where this programme prepares you |
| --- | --- | --- | --- | --- | --- |
| **Associate** | Oracle Certified Foundations Associate, Java | 1Z0-811 Java Foundations | Multiple choice; entry-level Java | Listed by prep providers in 2026; confirm availability with Oracle | Phase 1, Module 1 (topics 1.1–1.9) is well beyond this level |
| **Professional** | Oracle Certified Professional: Java SE 21 Developer | 1Z0-830 | 50 questions, 120 minutes, 68% to pass | Current | **Phase 1, Modules 1–2** (this programme's baseline is Java 21) |
| **Professional** | Oracle Certified Professional: Java SE 25 Developer | 1Z0-831 | 50 questions, 120 minutes, 68% to pass | Current, newest (Java 25 LTS) | Phase 1, Modules 1–2 plus the Java 22–25 notes marked in the lessons |
| Professional (older) | OCP Java SE 17 / 11 Developer | 1Z0-829 / 1Z0-819 | | Being retired; dates vary by source | Not recommended for new candidates |
| **Expert** | Oracle Certified Expert (Java EE components: EJB, JPA, web, web services) | | | **Retired.** Oracle currently offers no Expert-level Java SE certification | See "Expert level" below |

**Recommendation:** take **OCP Java SE 21 (1Z0-830)** after Phase 1 week 14, or **OCP Java SE 25 (1Z0-831)** if your target employers already run Java 25. Both have no prerequisite certification. The ITLearning programme page lists the OCP as covered by Modules 1–2, which matches the mapping below.

## Professional (OCP Java SE 21 / 25): objective-by-objective mapping

The 1Z0-831 objectives are shown; 1Z0-830 has the same ten domains without the Java 22–25 items (marked ²⁵).

| Exam domain and objective | Curriculum topic(s) | Coverage |
| --- | --- | --- |
| **Handling date, time, text, numeric and boolean values** | | |
| Primitives, wrappers, arithmetic and boolean expressions, Math API, precedence, conversions, casting | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md) | Full (var-args, Math API and evaluation rules section) |
| Text with String and StringBuilder, including text blocks | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md), [1.8](../phases/01-foundations/01-modern-java-oop/1.8-modern-java-features/README.md) | Full |
| Date, time, duration, period, instant, time zones, daylight saving | [1.9](../phases/01-foundations/01-modern-java-oop/1.9-exceptions-immutability-clean-code/README.md), [1.14](../phases/01-foundations/01-modern-java-oop/1.14-localization-formatting/README.md) | Full |
| **Program flow control** | | |
| if/else, switch statements and expressions, loops, break/continue | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md), [1.8](../phases/01-foundations/01-modern-java-oop/1.8-modern-java-features/README.md) | Full |
| **Object-oriented principles** | | |
| Objects, nested classes, object life cycle and garbage collection | [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md), [2.4](../phases/01-foundations/02-concurrency-jvm/2.4-jvm-memory-gc/README.md) | Full |
| Classes and records, fields, methods, constructors, flexible constructor bodies²⁵, initialisers | [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md), [1.8](../phases/01-foundations/01-modern-java-oop/1.8-modern-java-features/README.md) | Full |
| Overloading, including var-args | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md) | Full |
| Scope, encapsulation, immutable objects, `var`, unnamed variables²⁵ | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md), [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md), [1.8](../phases/01-foundations/01-modern-java-oop/1.8-modern-java-features/README.md), [1.9](../phases/01-foundations/01-modern-java-oop/1.9-exceptions-immutability-clean-code/README.md) | Full |
| Inheritance, abstract and sealed types, records, overriding (incl. Object methods), polymorphism | [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md), [1.8](../phases/01-foundations/01-modern-java-oop/1.8-modern-java-features/README.md) | Full |
| Interfaces, functional interfaces, private/static/default methods | [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md), [1.7](../phases/01-foundations/01-modern-java-oop/1.7-lambdas-streams/README.md) | Full |
| Enums with fields, methods and constructors | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md), [1.4](../phases/01-foundations/01-modern-java-oop/1.4-oop-and-solid/README.md) | Full |
| **Exception handling** | | |
| try/catch/finally, try-with-resources, multi-catch, custom exceptions | [1.9](../phases/01-foundations/01-modern-java-oop/1.9-exceptions-immutability-clean-code/README.md) | Full |
| **Arrays and collections** | | |
| Arrays, List, Set, Map, Deque, sequenced collections; add, remove, update, retrieve, sort | [1.3](../phases/01-foundations/01-modern-java-oop/1.3-syntax-types-control-flow/README.md), [1.5](../phases/01-foundations/01-modern-java-oop/1.5-collections-generics/README.md) | Full |
| **Streams and lambdas** | | |
| Object and primitive streams, lambdas, functional interfaces; filter, transform, sort | [1.7](../phases/01-foundations/01-modern-java-oop/1.7-lambdas-streams/README.md) | Full |
| Decomposition, concatenation, reduction, grouping, partitioning, gatherers²⁵, sequential and parallel | [1.7](../phases/01-foundations/01-modern-java-oop/1.7-lambdas-streams/README.md) | Full (Stream Gatherers section) |
| **Packaging and deploying Java code** | | |
| Modules, exports (incl. reflection/opens), dependencies, services, providers, consumers, module imports²⁵ | [2.5](../phases/01-foundations/02-concurrency-jvm/2.5-modules-packaging-jlink/README.md) | Full (added for the exam) |
| Compile and run, compact source files²⁵, instance main²⁵, multi-file source programs, jars, runtime images, migration with unnamed and automatic modules | [1.1](../phases/01-foundations/01-modern-java-oop/1.1-tooling-build-linux/README.md), [1.12](../phases/01-foundations/01-modern-java-oop/1.12-legacy-java-migration/README.md), [2.5](../phases/01-foundations/02-concurrency-jvm/2.5-modules-packaging-jlink/README.md) | Full |
| **Multithreading** | | |
| Platform and virtual threads, Runnable/Callable, thread life cycle, executors, concurrent API, scoped values²⁵ | [2.1](../phases/01-foundations/02-concurrency-jvm/2.1-threads-executors-completablefuture/README.md), [2.3](../phases/01-foundations/02-concurrency-jvm/2.3-virtual-threads-structured-concurrency/README.md) | Full |
| Thread-safe code with locks and the concurrent API | [2.2](../phases/01-foundations/02-concurrency-jvm/2.2-race-conditions-locks-atomics/README.md) | Full |
| Concurrent collections and parallel streams | [2.2](../phases/01-foundations/02-concurrency-jvm/2.2-race-conditions-locks-atomics/README.md), [1.7](../phases/01-foundations/01-modern-java-oop/1.7-lambdas-streams/README.md) | Full |
| **I/O** | | |
| Console and file I/O with streams; serialization; NIO.2 Path and Files | [1.10](../phases/01-foundations/01-modern-java-oop/1.10-io-nio-serialization/README.md) | Full (added for the exam) |
| **Localization** | | |
| Locales, resource bundles; formatting and parsing messages, dates, times, numbers, currency, percentages | [1.14](../phases/01-foundations/01-modern-java-oop/1.14-localization-formatting/README.md) | Full (added for the exam) |

**Gaps found and closed in this review:** I/O, NIO.2 and serialization (new topic 1.10), localization (new topic 1.14), modules, jars and `jlink` (new topic 2.5), plus new sections on var-args, the Math API and evaluation rules (1.2), enums with constructors, interface private/static/default methods, nested classes, initialisers and flexible constructor bodies (1.3), and Stream Gatherers (1.5).

### How to prepare for the OCP

1. Finish Phase 1 Modules 1–2 and pass every topic CBT at 90%+.
2. The OCP is a *language-lawyer* exam: it tests compile errors, edge cases and API details that real code rarely shows. Practise with timed mock exams (50 questions in 120 minutes) after Phase 1.
3. Use a reputable study guide for your exact exam code, and Oracle's own preparation material on MyLearn.
4. Type the tricky cases into `jshell` instead of guessing.
5. Book the exam within 6 months of buying a voucher (vouchers expire).

## Associate (Java Foundations)

The Foundations exam covers Java's features, JDK/JRE/JVM, basic syntax, data types, operators, control flow, simple classes and basic exceptions. Phase 1 topics 1.1–1.9 cover all of it in more depth. Because the OCP has no prerequisite, most candidates on this programme should skip the Associate exam and take the OCP directly; the Foundations credential mainly suits learners who want an early milestone after weeks 1–4.

## Expert level

Oracle's former Expert-level Java credentials covered Java EE components (EJB, JPA, web components, web services). They have been retired, and Oracle offers no Expert-level Java SE certification at the time of writing. Expert-level competence in this programme is evidenced differently:

| What employers look for at senior/expert level | Where you build it |
| --- | --- |
| Deep Java and JVM knowledge (concurrency, memory, performance) | Phase 1 Module 2; Phase 3 Module 13 (JFR, heap analysis) |
| Enterprise Java with Spring (security, data, testing) | Phase 1 Module 4; Phase 2 Modules 5–7 |
| Distributed systems, Kafka, Kubernetes, cloud | Phase 3 Modules 10–12 |
| Production ownership and system design | Every capstone's architecture section; Phase 3 Module 16 |

External certifications named on the ITLearning programme page complement the OCP at the higher levels: **Certified Kubernetes Application Developer (CKAD)** after Phase 3 Module 12, and **AWS Certified Developer – Associate** after Phase 2 Module 8 and Phase 3 Modules 12–13.

## Phases 2 and 3

The Oracle Java SE exams are fully covered by Phase 1. Phases 2 and 3 move into frameworks and platforms that Oracle's Java SE exams don't test (Spring Security, testing, CI/CD, microservices, Kafka, Kubernetes, observability, reactive Java, Spring AI). When those phases are built, their topic pages will reference the relevant external certifications (CKAD, AWS Developer) in the same objective-by-objective format.

## Sources

- [Getting Started with Java Certification (dev.java, by Oracle's Java team)](https://dev.java/learn/java-cert-overview/)
- [1Z0-831 OCP Java 25 exam objectives (Enthuware)](https://enthuware.com/oca-ocp-java-certification-resources/297-ocp-java-25-exam-syllabus)
- [Oracle certification (oracle.com)](https://www.oracle.com/education/certification/), the entry point to Oracle University's Java exams. Search there for **1Z0-830** (Java SE 21) or **1Z0-831** (Java SE 25); Oracle University's own exam pages were down for maintenance on 23 and 29 October 2026
