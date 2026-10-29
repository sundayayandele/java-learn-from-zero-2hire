## What you will be able to do

- Write lambdas and method references, and explain what a functional interface is.
- Use the core functional interfaces in `java.util.function`.
- Build stream pipelines with `filter`, `map`, `flatMap`, `sorted`, `distinct`, `limit` and terminal operations.
- Aggregate with `reduce` and with `Collectors` (`groupingBy`, `partitioningBy`, `toMap`, `summingX`, `teeing`).
- Use `Optional` correctly, and know when a plain loop is the better choice.

## Why this matters in a bank or fintech

Backend code spends much of its time turning one collection into another: filter today's debits, group them by merchant category, sum them per customer, find the top ten. Streams express that as a readable pipeline that reviewers can check line by line, instead of nested loops with temporary maps. Interviewers ask stream questions in nearly every Java screening, usually something like "group these transactions by account and sum them".

## 1. Lambdas and functional interfaces

A **functional interface** has exactly one abstract method. A **lambda** is a compact way to implement it inline.

```java
@FunctionalInterface
interface FeeRule {
    BigDecimal fee(BigDecimal amount);
}

FeeRule flat = amount -> new BigDecimal("10.00");
FeeRule percent = amount -> amount.multiply(new BigDecimal("0.005"));
FeeRule capped = amount -> {
    BigDecimal f = amount.multiply(new BigDecimal("0.005"));
    return f.min(new BigDecimal("2000.00"));
};
```

Syntax: `(params) -> expression` or `(params) -> { statements; return x; }`. Parameter types are usually inferred.

A lambda can use local variables from the enclosing method only if they are **effectively final** (never reassigned). That rule prevents confusing bugs where the lambda runs later, possibly on another thread.

### The built-in functional interfaces

You rarely need to declare your own. `java.util.function` has the common shapes:

| Interface | Method | Shape | Example |
| --- | --- | --- | --- |
| `Predicate<T>` | `test(T)` | T → boolean | `tx -> tx.amount().signum() < 0` |
| `Function<T,R>` | `apply(T)` | T → R | `Transaction::amount` |
| `Consumer<T>` | `accept(T)` | T → nothing | `tx -> ledger.post(tx)` |
| `Supplier<T>` | `get()` | nothing → T | `() -> UUID.randomUUID().toString()` |
| `UnaryOperator<T>` | `apply(T)` | T → T | `s -> s.strip().toUpperCase()` |
| `BinaryOperator<T>` | `apply(T,T)` | (T,T) → T | `BigDecimal::add` |
| `BiFunction<T,U,R>` | `apply(T,U)` | (T,U) → R | `(amount, rate) -> amount.multiply(rate)` |

Primitive specialisations (`IntPredicate`, `ToLongFunction`, `LongSupplier`...) avoid boxing costs in hot paths.

Predicates and functions compose:

```java
Predicate<Transaction> isDebit = tx -> tx.type() == Type.DEBIT;
Predicate<Transaction> isLarge = tx -> tx.amount().compareTo(new BigDecimal("1000000")) >= 0;
Predicate<Transaction> needsReview = isDebit.and(isLarge).and(Predicate.not(Transaction::isInternal));

Function<String, String> normalise = String::strip;
Function<String, String> normaliseName = normalise.andThen(String::toUpperCase);
```

### Method references

When a lambda only calls an existing method, a method reference is shorter and clearer:

| Kind | Syntax | Equivalent lambda |
| --- | --- | --- |
| Static method | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Instance method of a particular object | `ledger::post` | `tx -> ledger.post(tx)` |
| Instance method of an arbitrary object | `Transaction::amount` | `tx -> tx.amount()` |
| Constructor | `ArrayList::new` | `() -> new ArrayList<>()` |

## 2. Streams: the pipeline model

A **stream** is a sequence of elements flowing through a pipeline. It is not a data structure: it does not store anything, and it can be consumed only once.

```java
BigDecimal largeDebitsToday = transactions.stream()          // 1. source
        .filter(tx -> tx.valueDate().equals(today))           // 2. intermediate operations
        .filter(isDebit.and(isLarge))
        .map(Transaction::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);           // 3. terminal operation
```

- **Source**: `collection.stream()`, `Stream.of(...)`, `Arrays.stream(arr)`, `Files.lines(path)`, `IntStream.range(0, n)`.
- **Intermediate operations** return a new stream and are **lazy**: nothing happens until a terminal operation runs.
- **Terminal operations** produce a result or side effect and close the stream.

Laziness means the pipeline processes elements one at a time and can stop early: `filter(...).findFirst()` on a million transactions stops at the first match.

### Common intermediate operations

```java
.filter(predicate)                 // keep matching elements
.map(function)                     // transform each element
.mapToLong(tx -> tx.amountKobo())  // to a LongStream, for sum/average without boxing
.flatMap(c -> c.accounts().stream())   // one-to-many, then flatten
.distinct()                        // remove duplicates (uses equals/hashCode)
.sorted(Comparator.comparing(Transaction::valueDate))
.limit(10) / .skip(20)             // paging
.peek(tx -> log.debug("{}", tx))   // debugging only; don't put logic here
.takeWhile(p) / .dropWhile(p)      // on ordered streams: stop or start at the first non-match
```

`flatMap` is the one beginners find hardest. Picture it as "map each element to a stream, then join all those streams into one":

```java
List<Account> allAccounts = customers.stream()
        .flatMap(c -> c.accounts().stream())      // Stream<Customer> -> Stream<Account>
        .toList();
```

### Common terminal operations

```java
.toList()                          // Java 16+: an unmodifiable List
.collect(Collectors.toSet())
.forEach(consumer)                 // side effects; avoid mutating shared state
.count()
.anyMatch(p) / .allMatch(p) / .noneMatch(p)    // short-circuit
.findFirst() / .findAny()          // return Optional
.min(comparator) / .max(comparator)
.reduce(identity, accumulator)     // fold to one value
LongStream: .sum(), .average(), .summaryStatistics()
```

## 3. Collectors: grouping and aggregating

`Collectors` turn a stream into maps and summaries. These are the patterns interviews and real code use most.

```java
import static java.util.stream.Collectors.*;

// Total per account
Map<String, BigDecimal> totalByAccount = txs.stream()
        .collect(groupingBy(Transaction::account,
                reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)));

// Count per merchant category, sorted by category
Map<String, Long> countByMcc = txs.stream()
        .collect(groupingBy(Transaction::mcc, TreeMap::new, counting()));

// Split into two groups
Map<Boolean, List<Transaction>> flagged = txs.stream()
        .collect(partitioningBy(tx -> tx.amount().compareTo(threshold) > 0));

// Build a lookup; the third argument resolves duplicate keys (otherwise IllegalStateException)
Map<String, Account> byNumber = accounts.stream()
        .collect(toMap(Account::number, a -> a, (first, second) -> first));

// Several statistics in one pass
LongSummaryStatistics stats = txs.stream().mapToLong(Transaction::amountKobo).summaryStatistics();
stats.getMin(); stats.getMax(); stats.getAverage(); stats.getCount();

// Join strings
String csvHeader = Stream.of("date", "reference", "amount").collect(joining(","));

// Two collectors at once (Java 12+): total and count, combined
record Summary(BigDecimal total, long count) {}
Summary s = txs.stream().collect(teeing(
        reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add),
        counting(),
        Summary::new));
```

## 4. Optional

`Optional<T>` is a container that may or may not hold a value. It makes "there might be no result" explicit in a method's return type instead of returning `null`.

```java
Optional<Account> findByNumber(String number) { ... }

String owner = repo.findByNumber("0123456789")
        .map(Account::ownerName)
        .orElse("Unknown");

Account acc = repo.findByNumber(number)
        .orElseThrow(() -> new AccountNotFoundException(number));

repo.findByNumber(number).ifPresent(a -> notifier.send(a));
```

Rules that reviewers enforce:

- Use `Optional` as a **return type** for lookups. Do not use it for fields, method parameters or collections (return an empty list instead).
- Never call `get()` without checking: prefer `orElse`, `orElseGet`, `orElseThrow`, `map`, `ifPresent`.
- `orElse(expensiveCall())` evaluates the argument even when a value is present; use `orElseGet(() -> expensiveCall())` for anything costly.

### Stream Gatherers (Java 24+)

Java 24 finalised **gatherers** (JEP 485): custom intermediate operations, used with `stream.gather(...)`. Built-ins in `java.util.stream.Gatherers` cover common needs:

```java
transactions.stream().gather(Gatherers.windowFixed(100)).forEach(this::insertBatch);   // batches of 100
amounts.stream().gather(Gatherers.windowSliding(7)).map(this::average).toList();       // 7-day moving windows
amounts.stream().gather(Gatherers.scan(() -> BigDecimal.ZERO, BigDecimal::add)).toList();   // running balance
```

The baseline for this programme is Java 21, so gatherers are optional here, but the Java 25 OCP exam (1Z0-831) tests them.

## 5. Streams versus loops

Streams are not always better. Prefer a plain loop when:

- you need to break out early with complex conditions, or handle checked exceptions;
- the logic mutates several variables at once;
- the stream version needs comments to be understood.

Prefer streams for filter–map–collect transformations, where they read like the business rule. And avoid side effects inside streams:

```java
List<Transaction> flagged = new ArrayList<>();
txs.stream().filter(this::suspicious).forEach(flagged::add);       // works, but fragile
List<Transaction> flagged = txs.stream().filter(this::suspicious).toList();   // better
```

**Parallel streams** (`parallelStream()`) split work across the common fork-join pool. They help only for large, CPU-heavy, stateless work, and can hurt inside a web server where that pool is shared. Measure before using them; Module 2 covers better concurrency tools.

## Worked example: a merchant spend report

```java
record Txn(String account, String merchant, String mcc, BigDecimal amount, LocalDate date) {}

Map<String, BigDecimal> top5MerchantsLastMonth = txns.stream()
        .filter(t -> YearMonth.from(t.date()).equals(YearMonth.now().minusMonths(1)))
        .collect(groupingBy(Txn::merchant,
                reducing(BigDecimal.ZERO, Txn::amount, BigDecimal::add)))
        .entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .limit(5)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a, LinkedHashMap::new));      // LinkedHashMap keeps the sorted order
```

Read it top to bottom: last month's transactions, total per merchant, largest first, top five, in order. The equivalent loop needs a temporary map, a list of entries, a sort and a second loop.

## Common mistakes

- Reusing a stream: `IllegalStateException: stream has already been operated upon or closed`.
- `toMap` with duplicate keys and no merge function.
- Calling `Optional.get()` blindly, or returning `null` from a method declared to return `Optional`.
- Heavy side effects in `map` or `peek`.
- Expecting a lazy pipeline with no terminal operation to do anything.
- Using `parallelStream()` by default "for speed".

## Practice

1. From a list of transactions, compute: total credits, total debits, and net movement per account, in one `groupingBy` with a downstream collector.
2. Find the customer with the highest single debit last month and return it as `Optional<Customer>`.
3. Partition transactions into "needs review" and "auto-approved" using a composed `Predicate` built from three smaller ones.
4. Rewrite one stream from practice 1 as a loop. Which is easier to read? Write two sentences defending your choice in code review.

## Key terms

Functional interface, lambda, effectively final, method reference, `Predicate`, `Function`, `Consumer`, `Supplier`, stream, intermediate operation, terminal operation, laziness, `flatMap`, `reduce`, `Collectors.groupingBy`, `partitioningBy`, `toMap`, `teeing`, `Optional`, parallel stream.

## Further reading

- [The Java Tutorials: Lambda Expressions](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html)
- [`java.util.stream` package summary (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)
- [`Collectors` API (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
- [Stuart Marks: Optional — the mother of all bikesheds (talk slides and guidance)](https://stuartmarks.wordpress.com/2016/09/27/vjug24-session-on-optional/)
- [dev.java (Oracle): Lambda Expressions](https://dev.java/learn/language/fp/lambdas/)
- [dev.java (Oracle): The Stream API](https://dev.java/learn/api/collections-and-streams/streams/)
- [JEP 485: Stream Gatherers](https://openjdk.org/jeps/485)
