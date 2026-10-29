# Java fundamentals revision sheet for interviews

← [Home](../../README.md) · [Practice track](../../practice/README.md) · [Getting hired after Phase 1](../../phases/01-foundations/getting-hired/README.md) · [after Phase 2](../../phases/02-professional-backend/getting-hired/README.md)

The questions below come up in almost every junior and mid-level Java interview. For each one: the facts you must know, a 30-second answer to say out loud, and a self-test. The answers point back to the lessons for depth. Revise this sheet in the week before any interview, and practise the answers aloud: knowing something and explaining it under pressure are different skills.

## 1. `equals` and `hashCode`

- Contract: if `a.equals(b)` then `a.hashCode() == b.hashCode()`. The reverse need not hold (collisions are allowed).
- Override both, using the same fields. Never use mutable fields in either for objects stored in hash collections.
- Records generate both from their components; `BigDecimal.equals` compares scale (10.0 ≠ 10.00), so normalise or use `compareTo` (topic 1.8).

**Say it:** "`HashMap` finds the bucket with `hashCode` and then compares with `equals`. If I override `equals` without `hashCode`, two equal keys can land in different buckets and the map can't find my object. So I always override both, on the same immutable fields."

**Self-test:** implement `AccountKey` by hand ([practice problem](../../practice/dsa/src/main/java/ng/itlearning/practice/design/AccountKey.java)).

## 2. How `HashMap` works

- An array of buckets; the key's hash (spread with `h ^ (h >>> 16)`) picks a bucket; collisions form a linked list that becomes a red-black tree above 8 entries (when the table has at least 64 buckets).
- Average O(1) `get`/`put`; worst case O(log n) per bucket with trees. Resizes (doubles) when size exceeds capacity × load factor (0.75), rehashing entries.
- Allows one `null` key and `null` values. Not thread-safe; iteration order isn't guaranteed (`LinkedHashMap` keeps insertion or access order, `TreeMap` keeps keys sorted).

**Say it:** "It's an array of buckets indexed by the key's hash. Collisions chain and turn into a tree when a bucket gets crowded, so lookups stay fast. It resizes at 75% load. It isn't thread-safe; for concurrent use I'd take `ConcurrentHashMap` and its atomic `merge`/`compute`."

**Self-test:** why does a key whose `hashCode` always returns 42 still work, and what does it cost? (Topic 1.5.)

## 3. `ConcurrentHashMap` vs `synchronizedMap` vs `Hashtable`

- `ConcurrentHashMap`: fine-grained locking/CAS, concurrent reads without locking, atomic per-key operations (`compute`, `merge`, `putIfAbsent`), no `null` keys or values, weakly consistent iterators.
- `Collections.synchronizedMap`: one lock for everything; compound actions (`get` then `put`) are still unsafe without external locking.
- `Hashtable`: legacy, fully synchronised; don't use in new code.

**Self-test:** fix `map.put(k, map.get(k) + amount)` under concurrency ([practice problem](../../practice/dsa/src/main/java/ng/itlearning/practice/design/ConcurrentTally.java), topic 2.2).

## 4. Strings

- Immutable and final: safe to share, cache their hash, and use as keys. String literals are interned in the string pool; `new String("x")` creates a new object.
- `==` compares references; use `equals` (or `equalsIgnoreCase`). Concatenation in a loop creates many objects: use `StringBuilder` (not the synchronised `StringBuffer`).
- Text blocks (Java 15) for multi-line SQL and JSON; `String.format` and `formatted` use the default locale unless you pass one (topic 1.14).

## 5. Collections: which one and why

| Need | Choose | Avoid |
| --- | --- | --- |
| Index access, append | `ArrayList` | `LinkedList` (rarely faster in practice) |
| Stack or queue | `ArrayDeque` | `Stack`, `Vector` (legacy, synchronised) |
| Unique items, fast lookup | `HashSet` | `List.contains` in a loop (O(n²)) |
| Sorted keys, range queries | `TreeMap` (`floorKey`, `headMap`) | Sorting a list on every lookup |
| Top-k, scheduling | `PriorityQueue` | Full sort when k is small |
| Immutable constants | `List.of`, `Map.of`, `List.copyOf` | Exposing internal mutable lists |

**Say it:** "I pick by access pattern: hash-based for lookups, `ArrayDeque` for stacks and queues, `TreeMap` when I need order or ranges, and a heap for top-k."

## 6. Generics

- Type erasure: generic types are checked at compile time and erased at run time (no `new T()`, no `List<String>.class`).
- PECS: **P**roducer `extends`, **C**onsumer `super`. Read from `List<? extends Number>`, write into `List<? super Integer>`.
- Prefer generic methods (`static <T> List<T> firstN(...)`) to raw types; never use raw types in new code.

## 7. Exceptions

- Checked (`IOException`, `SQLException`): the caller can reasonably recover; unchecked (`IllegalArgumentException`, `NullPointerException`): programming errors or broken contracts. `Error`s (`OutOfMemoryError`) aren't meant to be caught.
- Wrap with the cause (`throw new StatementException("…", e)`), never swallow, log once where handled. `try`-with-resources closes resources and records `Suppressed` exceptions.
- In Spring, `@Transactional` rolls back on unchecked exceptions only by default (topic 4.8).

## 8. Immutability, `final`, `finally`, `finalize`

- `final` variable: assigned once; `final` class: can't be subclassed; `final` method: can't be overridden.
- `finally`: always runs after `try`/`catch` (except `System.exit` or a JVM crash); don't `return` from it.
- `finalize`: deprecated for removal; use `try`-with-resources or `Cleaner`.
- Immutable classes: `final` fields, no setters, defensive copies, `final` class or private constructor with factories. Benefits: thread safety, safe sharing, valid keys.

## 9. Concurrency essentials

- `synchronized`: mutual exclusion and visibility on one monitor. `volatile`: visibility only (a flag), not atomicity (`count++` is still a race).
- Atomics (`AtomicLong`, `LongAdder`) for single-variable lock-free updates; a check-then-act needs a lock or a CAS loop.
- Deadlock needs circular waiting: acquire locks in a consistent global order (topic 2.2). Diagnose with a thread dump (topic 2.6).
- `ExecutorService` instead of creating threads; shut it down. Virtual threads (Java 21) for many blocking tasks, not CPU-bound work (topic 2.3).

## 10. The JVM in one minute

- Heap (young and old generations, objects), stacks (frames per thread), metaspace (class metadata). G1 is the default collector; ZGC for very low pauses.
- `OutOfMemoryError: Java heap space` means retained objects; `Metaspace` means class loading leaks; containers need `-XX:MaxRAMPercentage` (topic 2.4).
- Diagnose with `jcmd`: `Thread.print`, `GC.heap_info`, `GC.heap_dump`, `JFR.start` (topic 2.6).

## 11. Modern Java you should be able to write on a whiteboard

- Records with a compact constructor; sealed interfaces with an exhaustive `switch`; pattern matching for `instanceof` and `switch`; text blocks; `var` for local variables where the type is obvious (topics 1.8, 1.12).
- Streams: `groupingBy` with `counting`/`summingLong`, `flatMap`, `toMap` with a merge function, `Comparator.comparing(...).thenComparing(...)` (topic 1.7).
- `java.time`: `Instant` for timestamps, `LocalDate` for business dates in a stated zone, `ZonedDateTime` for local times, `Duration` vs `Period`.

## 12. Money and numbers

- Never `double` or `float` for money: `BigDecimal` (constructed from strings or `valueOf`, with an explicit scale and `RoundingMode.HALF_EVEN` unless a policy says otherwise) or `long` minor units (kobo, cents).
- `int` overflows above about 2.1 billion: kobo totals need `long`; `Math.addExact` detects overflow.
- `Integer` caching: `Integer a = 1500, b = 1500; a == b` is false (cache is -128 to 127); compare with `equals`.

## 13. Questions to practise aloud (no notes)

1. Why must you override `hashCode` when you override `equals`?
2. What happens when you put a mutable key into a `HashSet` and then change it?
3. `ArrayList` vs `LinkedList` vs `ArrayDeque`: when would you use each?
4. Why is `String` immutable, and why does that matter for security and caching?
5. Checked vs unchecked exceptions, with an example of each from your capstone.
6. What does `volatile` guarantee, and what doesn't it?
7. How would you make a counter shared by many threads correct?
8. Explain PECS with a real method signature.
9. Why not `double` for money? What do you use, and how do you round?
10. What is a record, and when would you not use one?
11. How does a sealed interface make a `switch` safer?
12. Walk through what the JVM does when you run `java -jar app.jar` (class loading, JIT, GC) in one minute.

Then do the [SQL drills](../../practice/sql/README.md) and the Spring questions in topic 4.8; the mock interviews in the Getting Hired sections draw from the same pool.
