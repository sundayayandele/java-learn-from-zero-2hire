## What you will be able to do

- Recognise code written for Java 8, 11 and 17 and translate it into modern Java 21 idioms.
- Explain the main changes between LTS releases that affect existing code: modules and strong encapsulation, removed APIs, `javax` → `jakarta`, new garbage collectors.
- Plan and execute a migration safely: tests first, `jdeps`, compiler warnings, OpenRewrite recipes, one step at a time.
- Read unfamiliar legacy code methodically.

## Why this matters in a bank or fintech

Banks run Java code written over twenty years. Many of your first tickets will be in a service still on Java 8 or 11: fixing a bug, adding a field, or upgrading it because the old JDK is out of support and fails a security audit. Job ads say "Java 8/11/17/21" for exactly this reason. Engineers who can read old code without fear, and move it forward without breaking it, are the ones teams trust with critical systems.

## 1. A timeline you should know

| Release | Year | LTS | Changes you will see in code |
| --- | --- | --- | --- |
| Java 8 | 2014 | yes | Lambdas, streams, `Optional`, `java.time`, default methods |
| Java 9 | 2017 | | Module system (JPMS), `List.of`/`Map.of`, `jshell`, private interface methods |
| Java 10 | 2018 | | `var` |
| Java 11 | 2018 | yes | `HttpClient`, `String.isBlank/strip/lines/repeat`, `Files.readString`, single-file launch; **Java EE modules removed** (JAXB, JAX-WS, CORBA) |
| Java 14–16 | 2020–21 | | Switch expressions, records, text blocks, `instanceof` patterns, `Stream.toList()`, helpful NPEs |
| Java 17 | 2021 | yes | Sealed classes; **strong encapsulation of JDK internals** (no more `--illegal-access`); Security Manager deprecated |
| Java 21 | 2023 | yes | Virtual threads, record patterns, pattern matching for switch, sequenced collections, generational ZGC |
| Java 25 | 2025 | yes | Scoped values and module imports final; compact source files; Security Manager gone; 32-bit x86 removed |

Oracle, Adoptium and other vendors patch LTS releases for years, but each eventually reaches end of support. Running an unsupported JDK means unpatched vulnerabilities, which auditors and regulators flag.

## 2. Reading legacy code: translation table

When you open a Java 8 codebase you will see patterns like the left column. Recognise them, and use the right column in new code.

| Legacy idiom | Modern equivalent |
| --- | --- |
| `List<String> l = new ArrayList<String>();` | `var l = new ArrayList<String>();` or `List.of(...)` for constants |
| `Arrays.asList("a", "b")` for constants | `List.of("a", "b")` (truly unmodifiable, no nulls) |
| `Collections.unmodifiableList(new ArrayList<>(x))` | `List.copyOf(x)` |
| Anonymous inner class `new Comparator<Txn>() { public int compare(...) {...} }` | `Comparator.comparing(Txn::date)` |
| `for` loop building a filtered list | `stream().filter(...).toList()` |
| `.collect(Collectors.toList())` | `.toList()` (unmodifiable; check callers don't mutate the result) |
| `if (o instanceof Card) { Card c = (Card) o; ... }` | `if (o instanceof Card c) { ... }` |
| Long `if/else if` on a type or enum | Switch expression, pattern matching over a sealed type |
| `switch` with `case X: ... break;` | `case X -> ...` |
| POJO with constructor, getters, `equals`, `hashCode`, `toString` (or Lombok `@Value`) | `record` |
| String concatenation with `"\n" +` | Text block |
| `new Date()`, `Calendar`, `SimpleDateFormat` (not thread-safe!) | `Instant.now()`, `LocalDate`, `DateTimeFormatter` |
| `str.trim().isEmpty()` | `str.isBlank()` |
| `new String(Files.readAllBytes(p), UTF_8)` | `Files.readString(p)` |
| Apache `HttpClient` / `HttpURLConnection` boilerplate | `java.net.http.HttpClient` (or Spring's `RestClient`) |
| `Executors.newFixedThreadPool(200)` for blocking I/O | Virtual threads (topic 2.3) |
| `new Integer(5)`, `new BigDecimal(0.1)` | `Integer.valueOf(5)`, `new BigDecimal("0.1")` |
| `Optional` returned and then `.get()` | `.orElseThrow()`, `.map(...)` |

A small before/after:

```java
// Java 8 style
public List<String> largeDebitReferences(List<Transaction> txs) {
    List<String> refs = new ArrayList<String>();
    for (Transaction tx : txs) {
        if (tx instanceof DebitTransaction) {
            DebitTransaction d = (DebitTransaction) tx;
            if (d.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
                refs.add(d.getReference());
            }
        }
    }
    Collections.sort(refs);
    return Collections.unmodifiableList(refs);
}

// Java 21 style
public List<String> largeDebitReferences(List<Transaction> txs) {
    return txs.stream()
            .filter(tx -> tx instanceof DebitTransaction d && d.amount().compareTo(LARGE) > 0)
            .map(Transaction::reference)
            .sorted()
            .toList();
}
```

**Do not modernise for its own sake.** Rewriting working legacy code without tests is how outages happen. Modernise code you are already changing, behind tests, in small commits.

## 3. What actually breaks when you upgrade

### From Java 8 to 11

- **Removed Java EE modules.** `javax.xml.bind` (JAXB), `javax.xml.ws` (JAX-WS), `javax.annotation`, `javax.activation` and CORBA were deleted from the JDK. Code that compiled on 8 fails with `package javax.xml.bind does not exist`. Fix: add the libraries as normal Maven dependencies (now under the `jakarta.*` names; see below).
- **Internal APIs.** Code (or old libraries) using `sun.misc.BASE64Encoder` and similar internals. Fix: `java.util.Base64`, and upgrade the library.
- **Module system warnings** about illegal reflective access, from old versions of libraries such as Spring, Hibernate, Mockito, Lombok and ASM. Fix: upgrade those libraries first.
- `ClassLoader` changes: the system class loader is no longer a `URLClassLoader`; hacks that cast it break.

### From 11 to 17

- **Strong encapsulation of JDK internals.** The `--illegal-access` escape hatch is gone. Reflection into `java.*` private fields fails with `InaccessibleObjectException`. Old Lombok, Mockito, Byte Buddy, Groovy and Gradle versions are typical culprits. Fix: upgrade them; use `--add-opens` only as a documented, temporary workaround.
- Removed: Nashorn JavaScript engine, RMI activation, experimental AOT/Graal JIT.

### From 17 to 21

Usually smooth for application code. Watch for: libraries that need a version bump to understand Java 21 class files (ASM, Byte Buddy, Jacoco, Mockito, Lombok); deprecation of finalisation; and behaviour changes such as `Thread.stop` now always throwing. Default GC remains G1; consider Generational ZGC for low-latency services after measuring.

### `javax` → `jakarta`

Not a JDK change but the biggest migration in enterprise Java. When Oracle handed Java EE to the Eclipse Foundation, it became **Jakarta EE**, and from Jakarta EE 9 the package names changed from `javax.*` to `jakarta.*`: `javax.persistence.Entity` → `jakarta.persistence.Entity`, `javax.servlet` → `jakarta.servlet`, `javax.validation` → `jakarta.validation`. Spring Boot 3 moved to Jakarta EE 9/10, and Spring Boot 4 to Jakarta EE 11. Note that `javax.sql`, `javax.crypto` and other packages that are part of the JDK itself did **not** change. Topic 4.6 covers the Spring side.

## 4. A safe migration process

1. **Get a green build on the current version.** If tests are missing, write characterisation tests that pin down current behaviour, especially around money calculations and date handling.
2. **Inventory.** `./mvnw dependency:tree` for library versions; `jdeps --jdk-internals target/app.jar` to find uses of JDK internals; `jdeps --multi-release 21 --print-module-deps` to see which JDK modules the code needs.
3. **Upgrade build tooling and plugins first**: Maven 3.9+, `maven-compiler-plugin` with `<release>21</release>`, Surefire/Failsafe, JaCoCo, and Lombok/Mockito/Byte Buddy to versions that support Java 21.
4. **Move one LTS at a time** when the jump is large (8 → 11 → 17 → 21), getting the build green at each step. Run on the new JDK with the old `release` level first, then raise the language level.
5. **Automate the mechanical parts with OpenRewrite.** Recipes such as `org.openrewrite.java.migrate.UpgradeToJava21` and `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` rewrite imports, deprecated APIs and build files across the codebase in one reviewed commit:

   ```bash
   ./mvnw -U org.openrewrite.maven:rewrite-maven-plugin:run \
     -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-migrate-java:RELEASE \
     -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava21
   ```

   Run it on a branch, read the whole diff, and run every test before merging.
6. **Fix compiler warnings** with `-Xlint:all`: deprecations often become removals in the next LTS.
7. **Test in a production-like environment**: performance, memory (GC defaults and container awareness changed between 8 and 11), TLS (older protocols and ciphers are disabled by default), time zones and locales.
8. **Roll out gradually** and keep the previous build ready for rollback.

## 5. How to read an unfamiliar legacy codebase

- Start from the **entry points**: `main`, web controllers, scheduled jobs, message listeners. Follow one real request end to end in the debugger.
- Read the **tests** (if any) to learn what the code is supposed to do.
- Use **Find Usages** and **Call Hierarchy** in IntelliJ instead of guessing.
- Check **Git history** (`git log -p --follow File.java`, `git blame`) to see why something odd was done: the commit message often references a ticket or a regulator.
- Keep a short **notes file** of what you learn, and turn it into README content or an architecture decision record for the next person.
- Change one thing at a time, behind a test.

## Worked example: modernising a statement formatter

Legacy (Java 8):

```java
public class StatementLine {
    private final Date date; private final String narration; private final BigDecimal amount;
    public StatementLine(Date date, String narration, BigDecimal amount) { ... }
    public Date getDate() { return date; }            // leaks a mutable Date
    // getters, equals, hashCode, toString: 60 more lines
}

public static String format(List<StatementLine> lines) {
    SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");   // not thread-safe if made static
    StringBuilder sb = new StringBuilder();
    for (StatementLine l : lines) {
        sb.append(fmt.format(l.getDate())).append(" | ").append(l.getNarration())
          .append(" | ").append(l.getAmount()).append("\n");
    }
    return sb.toString();
}
```

Modern (Java 21):

```java
public record StatementLine(LocalDate date, String narration, BigDecimal amount) { }

private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");  // immutable, thread-safe

public static String format(List<StatementLine> lines) {
    return lines.stream()
            .map(l -> "%s | %s | %s".formatted(DATE.format(l.date()), l.narration(), l.amount()))
            .collect(Collectors.joining("\n", "", "\n"));
}
```

Before changing it, write a test that formats three known lines and asserts the exact output string; run it before and after.

## Common mistakes

- Upgrading the JDK and all libraries and the framework in one giant commit.
- Treating `--add-opens` flags as a permanent fix.
- Replacing `.collect(Collectors.toList())` with `.toList()` where callers later add to the list (it is unmodifiable).
- Running a global find-and-replace of `javax.` → `jakarta.` and breaking `javax.sql`/`javax.crypto` imports.
- Modernising untested code that works.

## Practice

1. Take the Java 8 `largeDebitReferences` and `StatementLine` examples, write tests for them, then modernise them and keep the tests green.
2. Clone any open-source Java 8 or 11 project, run `jdeps --jdk-internals` on its jar, and write a short migration risk list.
3. Create a small Java 11 Maven project that uses `javax.xml.bind.DatatypeConverter`, then migrate it to Java 21 (replacing it with `java.util.HexFormat` or `Base64`). Document each error and fix.
4. Run the OpenRewrite `UpgradeToJava21` recipe on a practice project and review the diff line by line. Which changes would you accept, and which would you question?

## Key terms

LTS, end of support, JPMS, strong encapsulation, `jdeps`, `--add-opens`, Jakarta EE, `javax` → `jakarta`, OpenRewrite, recipe, characterisation test, `release` flag.

## Further reading

- [Oracle JDK Migration Guide (Java 21)](https://docs.oracle.com/en/java/javase/21/migrate/getting-started.html)
- [OpenRewrite: Migrate to Java 21](https://docs.openrewrite.org/recipes/java/migrate/upgradetojava21)
- [`jdeps` tool reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeps.html)
- [Jakarta EE: the javax to jakarta namespace change (Eclipse Foundation)](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/)
- [Oracle: Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [dev.java (Oracle): Java release history and new features](https://dev.java/evolution/)
