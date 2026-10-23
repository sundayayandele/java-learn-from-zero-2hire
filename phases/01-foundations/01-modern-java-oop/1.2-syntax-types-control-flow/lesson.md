## What you will be able to do

- Declare variables with the right primitive or reference type, and explain how each is stored.
- Represent money correctly with `BigDecimal` and explain why `double` is wrong for it.
- Write decisions and loops with `if`, `switch` expressions, `for`, enhanced `for`, `while` and `do-while`.
- Write small, well-named methods with parameters and return values, and understand pass-by-value.
- Use `String`, `StringBuilder`, arrays and `var` appropriately.

## Why this matters in a bank or fintech

Most production incidents in financial software are not exotic. They are an `int` that overflowed when transaction volume grew, a `double` that turned ₦0.30 into ₦0.29999999999999998, a string compared with `==` instead of `equals`, or an off-by-one in a loop over a statement. This topic is about getting the basics exactly right, because in finance "almost right" is a reconciliation break.

## 1. Anatomy of a Java program

```java
package ng.itlearning.bank;              // the namespace; matches the folder path

import java.math.BigDecimal;             // use a class from another package

public class Account {                   // one public class per file, same name as the file
    private final String number;         // a field: state that belongs to each object
    private BigDecimal balance;

    public Account(String number, BigDecimal opening) {   // a constructor
        this.number = number;
        this.balance = opening;
    }

    public BigDecimal balance() {        // a method
        return balance;
    }

    public static void main(String[] args) {   // the program's entry point
        Account acc = new Account("0123456789", new BigDecimal("1500.00"));
        System.out.println(acc.balance());
    }
}
```

Statements end with `;`, blocks live inside `{ }`, and Java is case-sensitive. Names follow conventions that every reviewer checks: `PascalCase` for classes, `camelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants, all-lowercase for packages.

## 2. Types: primitives and references

Java is **statically typed**: every variable has a type the compiler knows, and it refuses to compile code that mixes types incorrectly. That catches whole classes of bugs before the code ever runs.

### The eight primitives

| Type | Size | Range / notes | Use for |
| --- | --- | --- | --- |
| `byte` | 8 bit | -128 to 127 | raw binary data |
| `short` | 16 bit | ±32 thousand | rarely used |
| `int` | 32 bit | about ±2.1 billion | counts, indexes |
| `long` | 64 bit | about ±9.2 quintillion | IDs, timestamps, amounts in minor units (kobo, cents) |
| `float` | 32 bit | ~7 decimal digits | almost never |
| `double` | 64 bit | ~15 decimal digits, binary fraction | scientific values, ratios, **never money** |
| `char` | 16 bit | one UTF-16 code unit | single characters |
| `boolean` | — | `true` / `false` | flags |

Primitives hold their value directly. Everything else (`String`, `BigDecimal`, arrays, your own classes) is a **reference type**: the variable holds a reference (an address) to an object on the heap. A reference can be `null`, which means "points to nothing", and calling a method on `null` throws `NullPointerException`.

Each primitive has a **wrapper class** (`Integer`, `Long`, `Double`, `Boolean`...) so it can live in collections. Java converts between them automatically (autoboxing), which has a trap: `Integer` values compared with `==` compare references, and only small values (-128 to 127) are cached.

```java
Integer a = 127, b = 127;   a == b   // true (cached)
Integer c = 1000, d = 1000; c == d   // false! different objects
c.equals(d)                          // true: compare values with equals
```

### Overflow: the silent bug

```java
int dailyVolumeKobo = 2_000_000_000;      // ₦20 million in kobo
dailyVolumeKobo += 500_000_000;            // wraps to a negative number. No error.
long safe = 2_000_000_000L + 500_000_000L; // use long
Math.addExact(2_000_000_000, 500_000_000); // or throw ArithmeticException on overflow
```

Underscores in numeric literals (`1_000_000`) are only for readability.

### Money: `BigDecimal`, never `double`

`double` stores numbers in binary, and most decimal fractions (0.1, 0.3) have no exact binary representation:

```java
System.out.println(0.1 + 0.2);            // 0.30000000000000004
```

Over millions of transactions those errors add up, and a ledger that does not balance to the kobo is a failed audit. Use `java.math.BigDecimal`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal price = new BigDecimal("0.10");      // ALWAYS construct from a String
BigDecimal wrong = new BigDecimal(0.1);          // 0.1000000000000000055511151231257827...
BigDecimal total = price.add(new BigDecimal("0.20"));         // 0.30 exactly

BigDecimal principal = new BigDecimal("250000.00");
BigDecimal monthlyRate = new BigDecimal("0.18")
        .divide(new BigDecimal("12"), 10, RoundingMode.HALF_EVEN);  // must give scale + rounding
BigDecimal interest = principal.multiply(monthlyRate)
        .setScale(2, RoundingMode.HALF_EVEN);    // round to kobo: 3750.00
```

Rules to remember:

- Construct from `String` (or `BigDecimal.valueOf(long)`), never from a `double` literal.
- `BigDecimal` is immutable: `a.add(b)` returns a new value; it does not change `a`.
- `divide` needs a scale and a `RoundingMode`, or it throws for non-terminating results like 1/3.
- `HALF_EVEN` ("banker's rounding") is the usual choice in finance because it does not bias totals upward.
- Compare with `compareTo`, not `equals`: `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` is `false` because the scales differ, while `compareTo` returns 0.

The alternative many payment systems use is a `long` holding **minor units** (kobo, cents), which is fast and exact for addition. Either way, the currency travels with the amount; you will model that properly in topic 1.6.

### `var`: local type inference

```java
var accounts = new ArrayList<Account>();   // the compiler infers ArrayList<Account>
var rate = new BigDecimal("0.18");
```

`var` works only for local variables with an initialiser. The variable is still statically typed. Use it when the type is obvious from the right-hand side; spell the type out when it is not (`var x = service.process();` hides what `x` is).

### Strings

`String` is an immutable sequence of characters. Every "modification" creates a new string.

```java
String name = "Ada";
name.toUpperCase();            // returns "ADA"; name is still "Ada"
String a = new String("NGN");
a == "NGN"                     // false: different objects
a.equals("NGN")                // true: compare strings with equals, always
"NGN".equals(currency)         // null-safe: works even if currency is null
String.format("%s owes ₦%,.2f", name, 1234.5)   // "Ada owes ₦1,234.50"
"  0123456789 ".strip()        // trim whitespace (Unicode-aware)
```

Building a long string in a loop with `+` creates thousands of throwaway objects; use `StringBuilder`:

```java
var sb = new StringBuilder();
for (var line : statementLines) sb.append(line).append('\n');
String statement = sb.toString();
```

### Arrays

```java
long[] dailyTotals = new long[31];            // fixed size, filled with 0
String[] currencies = {"NGN", "USD", "GBP"};
currencies.length                              // 3 (a field, not a method)
currencies[3]                                  // ArrayIndexOutOfBoundsException
```

Arrays have a fixed size. You will mostly use `List` instead (topic 1.4), but arrays still appear in method signatures (`main(String[] args)`) and performance-sensitive code.

## 3. Operators

Arithmetic `+ - * / %`, comparison `== != < > <= >=`, logical `&& || !` and assignment `+= -=` work as you would expect, with three traps:

- **Integer division truncates**: `7 / 2` is `3`. `7 / 2.0` is `3.5`.
- **`%` is remainder**, useful for "every 10th transaction" (`i % 10 == 0`) or check digits.
- **`&&` and `||` short-circuit**: in `acc != null && acc.isActive()` the second part runs only if the first is true, which is how you avoid a `NullPointerException`.

## 4. Control flow

### if / else

```java
if (amount.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("Amount must be positive");
} else if (amount.compareTo(dailyLimit) > 0) {
    return Decision.REQUIRES_APPROVAL;
} else {
    return Decision.APPROVED;
}
```

Always use braces, even for one line. The famous "goto fail" security bug was an `if` without braces.

### switch expressions (Java 14+)

The modern `switch` is an *expression*: it returns a value, uses `->` with no fall-through, and the compiler checks you covered every case of an enum.

```java
enum Channel { MOBILE, USSD, POS, BRANCH }

BigDecimal feeFor(Channel channel) {
    return switch (channel) {
        case MOBILE, USSD -> new BigDecimal("10.00");
        case POS -> new BigDecimal("25.00");
        case BRANCH -> {
            log("branch fee applied");
            yield new BigDecimal("50.00");     // yield returns a value from a block
        }
    };   // no default needed: all enum constants covered, so a new constant becomes a compile error
}
```

You will still read the old statement form with `case X:` and `break;` in legacy code. Forgetting `break` makes execution "fall through" to the next case, a classic bug that the arrow form eliminates. Topic 1.6 extends `switch` with pattern matching.

### Loops

```java
for (int i = 0; i < transactions.size(); i++) { ... }   // classic: when you need the index

for (Transaction tx : transactions) { ... }              // enhanced for: most of the time

while (retries < 3 && !sent) { sent = trySend(); retries++; }   // condition checked first

do { pin = prompt(); } while (!isValid(pin));            // body runs at least once
```

`break` leaves a loop; `continue` skips to the next iteration. Watch the boundaries: `i <= list.size()` reads one element past the end.

## 5. Methods

A method is a named, reusable block with inputs (parameters) and an optional output (return value):

```java
static BigDecimal monthlyInstalment(BigDecimal principal, BigDecimal annualRate, int months) {
    BigDecimal r = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_EVEN);
    BigDecimal onePlusRPowN = BigDecimal.ONE.add(r).pow(months);
    return principal.multiply(r).multiply(onePlusRPowN)
            .divide(onePlusRPowN.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_EVEN);
}
```

- **Overloading**: several methods may share a name if their parameter lists differ (`deposit(BigDecimal)` and `deposit(BigDecimal, String reference)`).
- **`static`** methods belong to the class, not to an object; call them as `LoanMath.monthlyInstalment(...)`. Instance methods need an object.
- **Java is always pass-by-value.** For a primitive the value is copied. For an object, the *reference* is copied: the method can change the object it points to, but reassigning the parameter does not affect the caller's variable.

```java
static void reassign(Account a) { a = new Account("999", BigDecimal.ZERO); }  // caller unaffected
static void mutate(Account a)   { a.deposit(new BigDecimal("100")); }          // caller sees the change
```

### Var-args, the Math API and evaluation rules

```java
static BigDecimal total(BigDecimal... amounts) {          // var-args: zero or more arguments, seen as an array
    return Arrays.stream(amounts).reduce(BigDecimal.ZERO, BigDecimal::add);
}
total();                                                  // allowed: empty array
total(a, b, c);
```

A var-arg parameter must be the last one, and only one is allowed. Overload resolution prefers an exact match, then widening, then boxing, and var-args last.

`Math` covers everyday numeric helpers: `Math.max/min/abs`, `Math.round` (returns `long` for `double`), `Math.floorDiv`/`floorMod` (correct for negatives, unlike `/` and `%`), `Math.pow`, `Math.sqrt`, and the overflow-checking `Math.addExact`/`multiplyExact`/`toIntExact`. For money keep using `BigDecimal`.

Evaluation rules the OCP exam loves: `*`, `/`, `%` bind tighter than `+` and `-`; `i++` yields the old value, `++i` the new one; compound assignment includes an implicit cast (`byte b = 10; b += 5;` compiles, `b = b + 5;` doesn't); `int` arithmetic on `byte`/`short`/`char` operands promotes them to `int`; and narrowing casts can lose information silently (`(int) 3_000_000_000L` is negative).

Good methods are short, do one thing, and have names that say what they return or do: `isOverdrawn()`, `calculateFee()`, `postTransfer()`.

## Worked example: a loan repayment schedule

```java
public static void printSchedule(BigDecimal principal, BigDecimal annualRate, int months) {
    BigDecimal instalment = monthlyInstalment(principal, annualRate, months);
    BigDecimal r = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_EVEN);
    BigDecimal balance = principal;
    for (int month = 1; month <= months; month++) {
        BigDecimal interest = balance.multiply(r).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal principalPart = (month == months) ? balance : instalment.subtract(interest);
        balance = balance.subtract(principalPart);
        System.out.printf("%2d  interest %10s  principal %10s  balance %12s%n",
                month, interest, principalPart, balance);
    }
}
```

The last month pays off whatever is left, so rounding pennies never leave a non-zero balance. That one line is the difference between a toy and a real amortisation schedule, and you will build the full version in Capstone 3.

## Common mistakes

- Using `double` or `float` for money, or `new BigDecimal(0.1)`.
- Comparing `String`, `Integer` or `BigDecimal` values with `==`.
- `int` overflow on counters and amounts that grow with business volume.
- Missing braces on `if`, or forgetting `break` in an old-style `switch`.
- Off-by-one loop bounds (`<=` vs `<`).
- Methods that do three things and are named after none of them.

## Practice

1. Write `FeeCalculator.fee(Channel, BigDecimal amount)`: a flat fee per channel, plus 0.5% for amounts over ₦100,000, capped at ₦2,000. Round to kobo with `HALF_EVEN`.
2. Print `0.1 + 0.2` as a `double` and as a `BigDecimal`, then explain the difference to a non-engineer in two sentences.
3. Write `isValidNuban(String)` that checks a 10-digit account number contains only digits. Stretch: research the NUBAN check-digit algorithm and implement it with a loop and `%`.
4. Build the repayment schedule above for ₦500,000 at 24% over 6 months and check that the final balance is exactly `0.00`.

## Key terms

Static typing, primitive, reference, wrapper class, autoboxing, overflow, `BigDecimal`, scale, rounding mode, immutability, switch expression, `yield`, enhanced for, pass-by-value, overloading, `static`.

## Further reading

- [The Java Tutorials: Language Basics](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html) (written for Java 8, still accurate for the basics)
- [`BigDecimal` API (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [JEP 361: Switch Expressions](https://openjdk.org/jeps/361)
- [JEP 286: Local-Variable Type Inference (`var`)](https://openjdk.org/jeps/286)
- [dev.java (Oracle): Language Basics](https://dev.java/learn/language/constructs/basics/)
- [The Java Language Specification, Java SE 21](https://docs.oracle.com/javase/specs/jls/se21/html/index.html)
