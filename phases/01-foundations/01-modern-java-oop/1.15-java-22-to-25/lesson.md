> **Why this topic is here.** This programme's baseline is Java 21, because most employers run it today. Java 25 (September 2025) is the newest long-term-support (LTS) release, and teams are starting to move to it. This optional topic covers what changed between 21 and 25, so you can read Java 25 code, answer "what's new in Java?" in an interview, and plan an upgrade. It isn't examined.

## What you will be able to do

- Explain the Java release model and why teams move from one LTS release (21) to the next (25).
- Use the language features finalised in Java 22–25: unnamed variables and patterns, flexible constructor bodies, module import declarations, and compact source files with instance `main` methods.
- Process streams with gatherers (`Gatherers.windowFixed`, `scan`, `fold`, `mapConcurrent`).
- Explain scoped values and why virtual threads no longer pin on `synchronized`.
- Recognise preview features, and know which JVM and security changes affect an upgrade.

## Why this matters in a bank or fintech

Banks upgrade Java slowly and deliberately: every upgrade needs a regression run, a security review and a rollback plan. Engineers who know what changed between LTS releases make those upgrades boring, which is exactly what a bank wants. Several Java 24–25 changes matter directly to payment systems: virtual threads that no longer pin on `synchronized` (so I/O-heavy services scale without rewrites), quantum-resistant cryptography APIs, and smaller object headers that cut memory use.

## 1. The release model in one minute

Java ships a feature release every six months (March and September). Every two years one release is **LTS**: vendors patch it for many years. Recent LTS releases are 17 (2021), **21** (2023) and **25** (2025); 22, 23 and 24 were short-lived feature releases whose features are all in 25.

A feature can arrive as:

| Status | Meaning | Use it in production? |
| --- | --- | --- |
| **Incubator** | An API in a `jdk.incubator.*` module, still changing | No |
| **Preview** | Complete, but can still change; needs `--enable-preview` at compile and run time | No |
| **Final** (standard) | Part of the platform, with compatibility guarantees | Yes |

When you read "JEP 485" below, that's the JDK Enhancement Proposal that defined the feature; [openjdk.org/jeps](https://openjdk.org/jeps/0) has them all.

## 2. Language features finalised in Java 22–25

### Unnamed variables and patterns: `_` (Java 22, JEP 456)

When a variable must exist but you never use it, name it `_`:

```java
for (Transfer _ : transfers) { count++; }           // only counting

try {
    amount = new BigDecimal(input);
} catch (NumberFormatException _) {                 // the exception object isn't needed
    return Result.rejected("bad amount");
}

if (o instanceof Transfer(String ref, _, _)) {       // only the reference matters
    log.info("transfer {}", ref);
}
```

It tells the reader (and the compiler) "deliberately unused", and it removes warnings about unused variables.

### Flexible constructor bodies (Java 25, JEP 513)

Until Java 25, `super(...)` had to be the first statement in a constructor, so validating arguments before calling the parent constructor needed awkward static helper methods. Now statements that don't use `this` can come first:

```java
class SavingsAccount extends Account {
    private final BigDecimal rate;

    SavingsAccount(String number, BigDecimal rate) {
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("0.30")) > 0) {
            throw new IllegalArgumentException("rate out of range: " + rate);
        }
        this.rate = rate;          // fields may be initialised before super(...) too
        super(number);
    }
}
```

On Java 21 this fails with `call to super must be first statement in constructor`. Validating first means a half-built object never exists, and the parent constructor can safely call overridable methods that read `rate`.

### Module import declarations (Java 25, JEP 511)

`import module java.base;` imports every public type of the packages a module exports: `List`, `Map`, `BigDecimal`, `LocalDate`, `Path` and more, in one line. It's handy for scripts, examples and teaching. In production code most teams keep explicit imports, which IDEs manage anyway; if two modules export types with the same simple name, you still need a single-type import to choose.

### Compact source files and instance `main` methods (Java 25, JEP 512)

A small program no longer needs a class declaration or `public static void main(String[] args)`:

```java
void main() {
    String name = IO.readln("Your name: ");
    IO.println("Welcome to ITLearning Bank, " + name);
}
```

Save it as `Hello.java` and run `java Hello.java`. The new `java.lang.IO` class gives you `println`, `print` and `readln` without `System.out` or `Scanner`, and a compact source file automatically imports `java.base`. This is mainly for learning and scripts: real services still use classes, packages and Spring Boot's `main`. (On Java 21, the week-0 on-ramp's classic `public static void main` is what works.)

### Markdown documentation comments (Java 23, JEP 467)

Javadoc can now be written in Markdown, with `///` comments:

```java
/// Calculates the transfer fee, including 7.5% VAT.
///
/// - Amounts up to ₦5,000: ₦10 plus VAT
/// - Above: ₦25 plus VAT
///
/// @param amount the transfer amount in naira; must be positive
/// @return the fee, rounded half-even to the kobo
public BigDecimal transferFee(BigDecimal amount) { ... }
```

### Launching multi-file programs (Java 22, JEP 458)

`java Main.java` can now run a program spread over several source files in the same directory tree, compiling the others on demand. It's useful for quick tools before a project deserves Maven.

## 3. Stream gatherers (Java 24, JEP 485)

Streams had a fixed set of intermediate operations (`map`, `filter`, `flatMap`…). **Gatherers** let you add your own, and Java ships useful ones in `java.util.stream.Gatherers`:

```java
// Batches of 100 for a partner's bulk-payment API
List<List<Transfer>> batches = transfers.stream()
        .gather(Gatherers.windowFixed(100))
        .toList();

// Running balance after each transaction
List<BigDecimal> running = transactions.stream()
        .map(Transaction::amount)
        .gather(Gatherers.scan(() -> opening, BigDecimal::add))
        .toList();

// Three-day moving windows of daily volumes for a fraud rule
List<List<BigDecimal>> windows = dailyVolumes.stream()
        .gather(Gatherers.windowSliding(3))
        .toList();

// Call a rate API for each currency, at most 8 calls at a time, on virtual threads, keeping order
List<Rate> rates = currencies.stream()
        .gather(Gatherers.mapConcurrent(8, rateClient::fetch))
        .toList();
```

Before gatherers, each of these needed a loop or a custom collector. `fold` (one result from many, like `reduce` but with a different result type) completes the set. You can write your own with `Gatherer.of(...)`, but the built-in ones cover most needs.

## 4. Concurrency: scoped values and no more pinning

### Virtual threads don't pin on `synchronized` (Java 24, JEP 491)

On Java 21, a virtual thread that blocked inside a `synchronized` block **pinned** its carrier (platform) thread, which could stall a service that used virtual threads heavily (topic 2.3 showed the `jdk.VirtualThreadPinned` event). From Java 24, virtual threads can unmount inside `synchronized`, so older libraries that use it no longer limit scaling. That alone is a strong reason for I/O-heavy services on virtual threads to move to 25.

### Scoped values (Java 25, JEP 506)

A `ScopedValue` shares an immutable value with everything a piece of code calls, for a bounded scope: the modern replacement for many uses of `ThreadLocal`, and cheap with millions of virtual threads.

```java
static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

void handle(Request request) {
    ScopedValue.where(REQUEST_ID, request.id()).run(() -> transferService.transfer(request));
}

// deep in the call stack, no parameter passing:
void audit(String event) {
    log.info("[{}] {}", REQUEST_ID.orElse("no-request"), event);
}
```

Unlike a `ThreadLocal`, the value can't be changed by callees, disappears when the scope ends (no leaks in pooled threads), and is inherited by subtasks in structured concurrency.

**Structured concurrency** (`StructuredTaskScope`, topic 2.3) is still a **preview** in Java 25, with a revised API. Read about it; don't ship it without `--enable-preview` sign-off.

## 5. JVM, performance and security changes worth knowing

| Change | Release | What it means for you |
| --- | --- | --- |
| Generational ZGC is the default ZGC mode; the old mode is removed | 23, 24 | `-XX:+UseZGC` gives generational ZGC; drop `-XX:+ZGenerational` |
| Compact object headers (`-XX:+UseCompactObjectHeaders`) | 25 (product feature, opt-in) | Object headers shrink from 12 to 8 bytes: often 10–20% less heap for object-heavy services |
| Ahead-of-time class loading and linking, and simpler AOT cache creation | 24, 25 | Faster start-up from an AOT cache made in a training run (`-XX:AOTCacheOutput=app.aot`) |
| Security Manager permanently disabled | 24 | Old code calling `System.setSecurityManager` fails; it was deprecated since 17 |
| Quantum-resistant ML-KEM and ML-DSA algorithms | 24 | Key encapsulation and signatures designed to resist future quantum attacks, for long-lived financial data |
| Key Derivation Function API (HKDF) | 25 | A standard API for deriving keys, instead of hand-rolled code |
| Foreign Function and Memory API | 22 | Call native libraries (an HSM vendor's C library, say) without JNI |
| Class-File API | 24 | For tools and frameworks that generate bytecode; you'll meet it indirectly |

## 6. Upgrading a service from 21 to 25

1. **Check your stack supports 25.** Spring Boot 4 supports Java 17 through 25. Update build plugins (Maven Compiler, Surefire, JaCoCo, Mockito/Byte Buddy) to versions that understand Java 25 class files.
2. **Build and test on both** in CI (a matrix of `java-version: ["21", "25"]`) before switching.
3. **Set `<maven.compiler.release>25</maven.compiler.release>`** only when every runtime, container image and build agent has Java 25.
4. **Read the warnings:** `sun.misc.Unsafe` memory-access methods warn at run time (since Java 24), and agents loaded into a running JVM warn unless explicitly enabled (since Java 21).
5. **Measure:** compare heap, GC pauses and start-up before and after; try compact object headers.
6. **Roll out like any release:** canary, watch the dashboards, keep the previous image ready.

## Worked example: a statement job, then and now

Java 21:

```java
public static void main(String[] args) {
    List<Transaction> txns = loader.load(args[0]);
    List<List<Transaction>> pages = new ArrayList<>();
    for (int i = 0; i < txns.size(); i += 50) {
        pages.add(txns.subList(i, Math.min(i + 50, txns.size())));
    }
    for (List<Transaction> page : pages) {
        printer.print(page);
    }
}
```

Java 25:

```java
void main(String[] args) {
    loader.load(args[0]).stream()
          .gather(Gatherers.windowFixed(50))
          .forEach(printer::print);
}
```

Same behaviour, and the paging logic (where off-by-one bugs hide) is gone. Remember that code like this only compiles where Java 25 is the minimum, so in a shared library you'd wait until every consumer has upgraded.

## Common mistakes

- Shipping preview features (structured concurrency, primitive patterns) to production, or forgetting that `--enable-preview` code only runs on the exact Java version it was compiled for.
- Raising `maven.compiler.release` to 25 before the runtime image and CI agents have Java 25 (`UnsupportedClassVersionError`).
- Using `import module` in production code where a name clash makes imports ambiguous.
- Treating `ScopedValue` like a mutable `ThreadLocal`: it can't be reassigned inside the scope.
- Assuming compact object headers are on by default in 25: they are opt-in.

## Practice

1. Install Temurin 25 next to 21 (SDKMAN!: `sdk install java 25-tem`) and run the compact `Hello.java` above.
2. Rewrite one loop from your Module 1 project with a gatherer (`windowFixed`, `scan` or `windowSliding`), and keep the tests green.
3. Add a constructor that validates before `super(...)` to a class in your project, and compile it on both 21 and 25 to see the difference.
4. Add a CI matrix that builds your Module 1 project on Java 21 and 25.
5. In three sentences, explain to a non-technical manager why a payment API on virtual threads benefits from moving to Java 25.

## Key terms

LTS, feature release, JEP, preview, incubator, unnamed variable, flexible constructor body, module import declaration, compact source file, instance main method, `IO`, Markdown doc comment, gatherer, `windowFixed`, `scan`, `mapConcurrent`, pinning, scoped value, compact object headers, AOT cache, ML-KEM, ML-DSA.

## Further reading

- [JDK 25 release (OpenJDK)](https://openjdk.org/projects/jdk/25/) and the [JEP index](https://openjdk.org/jeps/0)
- [JEP 485: Stream Gatherers](https://openjdk.org/jeps/485)
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- [JEP 506: Scoped Values](https://openjdk.org/jeps/506)
- [JEP 512: Compact Source Files and Instance Main Methods](https://openjdk.org/jeps/512)
- [JEP 513: Flexible Constructor Bodies](https://openjdk.org/jeps/513)
- [dev.java: Learn Java](https://dev.java/learn/)
