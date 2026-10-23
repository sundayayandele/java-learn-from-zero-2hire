> **Why this topic is here.** Kotlin is not on the ITLearning programme page, but it was added to this programme on purpose: many fintechs and banks in Nigeria, the UK and Europe run Kotlin services alongside Java, Spring Boot supports it as a first-class language, and Android work is Kotlin-first. You do not need to become a Kotlin expert in Phase 1. You need to read it fluently, write simple code, and mix it with Java in one project. Topic 4.7 applies it to Spring Boot.

## What you will be able to do

- Explain what Kotlin is, why JVM teams adopt it, and how it interoperates with Java.
- Write Kotlin with `val`/`var`, null safety, functions, classes, data classes, sealed classes, `when` and string templates.
- Use Kotlin's collection operations and extension functions.
- Add Kotlin to a Maven project and call Java from Kotlin and Kotlin from Java.

## Why this matters in a bank or fintech

Kotlin removes whole categories of the bugs you met in topics 1.2–1.7: the compiler tracks nullability, so `NullPointerException` mostly disappears; data classes replace boilerplate; and immutable `val` is the default. Teams that adopt it usually keep their Java code and add Kotlin file by file, because both compile to the same JVM bytecode and call each other freely. A Java engineer who can review a Kotlin pull request is more useful to those teams on day one.

## 1. Kotlin in one paragraph

Kotlin is a statically typed JVM language created by JetBrains (the makers of IntelliJ IDEA) and released in 2016. It compiles to Java bytecode, runs on the same JVM, uses the same libraries (Spring, Hibernate, JUnit, Jackson) and builds with Maven or Gradle. The current major line is Kotlin 2.x with the K2 compiler. Spring Boot 4 requires Kotlin 2.2 or later.

## 2. Syntax side by side

```kotlin
// Kotlin                                         // Java
val rate = 0.18                                   // final double rate = 0.18;     (val = read-only)
var balance = BigDecimal("1500.00")               // BigDecimal balance = new BigDecimal("1500.00");
val name: String = "Ada"                          // types are inferred, or declared after a colon

fun fee(amount: BigDecimal): BigDecimal {         // static BigDecimal fee(BigDecimal amount) {
    return amount.multiply(BigDecimal("0.005"))
}
fun double(x: Int) = x * 2                        // single-expression function

println("Balance for $name is ${balance.setScale(2)}")   // string templates
```

- No semicolons, no `new`.
- `val` (read-only reference, like `final`) is the default choice; use `var` only when you need to reassign.
- Functions can live at the top level of a file: no utility class needed.
- `==` in Kotlin calls `equals` (structural equality), so string comparison just works. `===` compares references.
- Default and named arguments replace most overloads and builders:

```kotlin
fun transfer(from: String, to: String, amount: BigDecimal, narration: String = "Transfer") { ... }
transfer(from = "0123456789", to = "9876543210", amount = BigDecimal("5000"))
```

## 3. Null safety

The headline feature. Types are non-nullable unless you add `?`:

```kotlin
var accountName: String = "Ada"
accountName = null                 // compile error

var middleName: String? = null     // nullable type
middleName.length                  // compile error: might be null
middleName?.length                 // safe call: null if middleName is null
middleName?.length ?: 0            // Elvis operator: default when null
middleName!!.length                // "I promise it's not null": throws NPE if it is. Avoid.

if (middleName != null) {
    println(middleName.length)     // smart cast: the compiler knows it's non-null here
}
```

The compiler forces you to deal with "might be missing" at compile time instead of discovering it in production at 2 a.m.

**Platform types.** Values coming from Java code have unknown nullability (shown as `String!` in the IDE). Kotlin trusts you; if you treat a Java `null` as non-null, you still get an NPE. Annotating Java APIs with nullability annotations (JSpecify's `@Nullable`/`@NonNull`, which Spring Framework 7 uses throughout) lets Kotlin see the truth.

## 4. Classes, data classes and objects

```kotlin
class Account(val number: String, private var balance: BigDecimal) {   // primary constructor declares properties
    init {
        require(number.matches(Regex("\\d{10}"))) { "Account number must be 10 digits" }
    }

    fun withdraw(amount: BigDecimal) {
        require(amount.signum() > 0) { "Amount must be positive" }
        check(balance >= amount) { "Insufficient funds" }   // operators work on Comparable
        balance -= amount                                   // BigDecimal operators via Kotlin stdlib
    }

    fun balance() = balance
}
```

- Classes are `final` by default; mark them `open` to allow subclassing. (Spring needs proxies on some classes, which is why Spring projects use the `kotlin-spring` compiler plugin: topic 4.7.)
- Properties replace fields plus getters/setters. From Java, `account.number` appears as `getNumber()`.
- `require` throws `IllegalArgumentException`, `check` throws `IllegalStateException`: guard clauses in one line.

**Data classes** are Kotlin's records, with one extra:

```kotlin
data class Money(val amount: BigDecimal, val currency: String)

val a = Money(BigDecimal("100.00"), "NGN")
val b = a.copy(amount = BigDecimal("250.00"))   // copy with changes: immutable updates made easy
val (amt, ccy) = a                              // destructuring
```

`object` declares a singleton, and a `companion object` holds what Java would make `static`:

```kotlin
object FeeSchedule { val TRANSFER_FEE = BigDecimal("10.75") }

class AccountNumber private constructor(val value: String) {
    companion object {
        fun of(raw: String): AccountNumber = AccountNumber(raw.trim())
    }
}
```

## 5. Sealed classes and `when`

Kotlin had sealed hierarchies and exhaustive `when` long before Java 21, and they look very similar:

```kotlin
sealed interface PaymentMethod
data class Card(val token: String, val last4: String) : PaymentMethod
data class BankTransfer(val bankCode: String, val account: String) : PaymentMethod
data class Wallet(val id: String) : PaymentMethod

fun fee(method: PaymentMethod, amount: BigDecimal): BigDecimal = when (method) {
    is Card -> (amount * BigDecimal("0.015")).min(BigDecimal("2000.00"))   // smart cast to Card
    is BankTransfer -> BigDecimal("10.75")
    is Wallet -> BigDecimal("5.00")
}   // exhaustive: adding a subtype breaks compilation until handled
```

`when` also replaces `switch` on values and ranges: `when (score) { in 700..850 -> "A"; in 600 until 700 -> "B"; else -> "C" }`.

## 6. Collections and lambdas

Kotlin distinguishes read-only and mutable collections in the type system, and has collection operations built in (no `.stream()`/`.collect()`):

```kotlin
val txs: List<Txn> = loadTransactions()              // read-only List
val mutable = mutableListOf<Txn>()                   // MutableList

val totalByAccount: Map<String, BigDecimal> = txs
    .filter { it.type == Type.DEBIT }                // `it` is the implicit single parameter
    .groupBy { it.account }
    .mapValues { (_, list) -> list.sumOf { it.amount } }

val top5 = txs.sortedByDescending { it.amount }.take(5)
val (large, small) = txs.partition { it.amount > BigDecimal("1000000") }
```

These operations are eager (each step builds a list). For very large data or long chains, `txs.asSequence()` makes them lazy like Java streams.

**Extension functions** add methods to existing types without inheritance:

```kotlin
fun String.maskAccount(): String = "******" + takeLast(4)
"0123456789".maskAccount()      // ******6789

fun BigDecimal.toKobo(): Long = (this * BigDecimal(100)).longValueExact()
```

**Scope functions** (`let`, `apply`, `also`, `run`, `with`) are concise but easy to overuse. The two you will see most: `x?.let { ... }` to run code only when `x` is non-null, and `Builder().apply { a = 1; b = 2 }` to configure an object.

## 7. Interoperability with Java

Java and Kotlin classes can live in the same Maven module and call each other.

Calling Java from Kotlin is natural: getters become properties (`customer.name` calls `getName()`), and static methods are called on the class.

Calling Kotlin from Java needs a few annotations for a natural API:

```kotlin
class RateTable {
    companion object {
        @JvmStatic fun standard(): RateTable = RateTable()   // Java: RateTable.standard()
        const val VERSION = "2026-10"                        // Java: RateTable.VERSION
    }
}
```

```kotlin
// MoneyUtils.kt: the file annotation must be the first line of the file
@file:JvmName("MoneyUtils")                // top-level functions appear as MoneyUtils.round(...) in Java

package ng.itlearning.bank

@JvmOverloads fun round(x: BigDecimal, scale: Int = 2) = x.setScale(scale, RoundingMode.HALF_EVEN)
```

Kotlin has **no checked exceptions**. Java callers of a Kotlin function that throws `IOException` will not be forced to catch it unless the function is annotated `@Throws(IOException::class)`.

### Adding Kotlin to a Maven build

```xml
<properties>
  <kotlin.version>2.4.21</kotlin.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-stdlib</artifactId>
    <version>${kotlin.version}</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-maven-plugin</artifactId>
      <version>${kotlin.version}</version>
      <configuration><jvmTarget>21</jvmTarget></configuration>
      <executions>
        <execution><id>compile</id><goals><goal>compile</goal></goals>
          <configuration><sourceDirs>
            <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
            <sourceDir>${project.basedir}/src/main/java</sourceDir>
          </sourceDirs></configuration>
        </execution>
        <execution><id>test-compile</id><goals><goal>test-compile</goal></goals></execution>
      </executions>
    </plugin>
    <!-- maven-compiler-plugin must run after Kotlin so Java can see Kotlin classes;
         see the Kotlin Maven docs for the exact execution overrides. -->
  </plugins>
</build>
```

The Kotlin compiler must run before `javac` in a mixed project so each language can see the other's classes. The [Kotlin Maven guide](https://kotlinlang.org/docs/maven.html) has the exact configuration, and start.spring.io generates it for you when you choose Kotlin (topic 4.7).

## 8. Coroutines, briefly

Kotlin's answer to asynchronous code is **coroutines** (`suspend` functions, `launch`, `async`), which let you write non-blocking code that reads sequentially. Java 21's virtual threads (topic 2.3) solve much of the same problem for blocking code. In Spring MVC services, virtual threads are usually enough; coroutines matter more in Spring WebFlux and Android. You only need to recognise `suspend fun` in Phase 1.

## Worked example: the same rule in both languages

```java
// Java 21
public record Txn(String account, Type type, BigDecimal amount) { }

static Map<String, BigDecimal> debitTotals(List<Txn> txs) {
    return txs.stream()
            .filter(t -> t.type() == Type.DEBIT)
            .collect(Collectors.groupingBy(Txn::account,
                    Collectors.reducing(BigDecimal.ZERO, Txn::amount, BigDecimal::add)));
}
```

```kotlin
// Kotlin
data class Txn(val account: String, val type: Type, val amount: BigDecimal)

fun debitTotals(txs: List<Txn>): Map<String, BigDecimal> =
    txs.filter { it.type == Type.DEBIT }
       .groupBy { it.account }
       .mapValues { (_, list) -> list.sumOf { it.amount } }
```

Same JVM, same `BigDecimal`, same result. Choose the language your team uses; be able to read both.

## Common mistakes

- Using `!!` to silence the compiler instead of handling null.
- Forgetting that classes are final by default (frameworks that proxy classes need `open` or the `kotlin-spring` plugin).
- Assuming Java values are non-null (platform types).
- Expecting Java callers to be forced to catch Kotlin exceptions.
- Overusing scope functions until code is unreadable.

## Practice

1. Install the Kotlin plugin (bundled in IntelliJ) and write `Money`, `Account` and the `PaymentMethod` sealed hierarchy in Kotlin.
2. In one Maven project, write a Java `FeeEngine` that calls a Kotlin `RateTable`, and a Kotlin test that calls the Java engine.
3. Rewrite three of your Java stream exercises from topic 1.5 in Kotlin collection operations.
4. Write a Kotlin function `parseAmount(raw: String?): BigDecimal?` that returns null for blank or invalid input without ever using `!!`.

## Key terms

Kotlin, `val`/`var`, type inference, null safety, nullable type, safe call `?.`, Elvis `?:`, `!!`, smart cast, platform type, data class, `copy`, `object`, companion object, sealed class, `when`, extension function, scope function, `@JvmStatic`, `@JvmOverloads`, coroutine.

## Further reading

- [Kotlin docs: Basic syntax](https://kotlinlang.org/docs/basic-syntax.html)
- [Kotlin docs: Comparison to Java](https://kotlinlang.org/docs/comparison-to-java.html)
- [Kotlin docs: Calling Java from Kotlin](https://kotlinlang.org/docs/java-interop.html)
- [Kotlin docs: Calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Kotlin docs: Maven](https://kotlinlang.org/docs/maven.html)
- [Kotlin docs: Null safety](https://kotlinlang.org/docs/null-safety.html)
