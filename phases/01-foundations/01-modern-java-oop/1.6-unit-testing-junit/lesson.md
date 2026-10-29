## What you will be able to do

- Explain what a unit test is and why every project in this programme needs them.
- Write JUnit tests with the Arrange–Act–Assert shape and clear names.
- Use AssertJ (and plain JUnit) assertions, including the right way to compare `BigDecimal`.
- Test that invalid input throws the right exception.
- Cover boundaries with parameterised tests instead of copy-pasted ones.
- Run tests with Maven and in IntelliJ, and read a failure report.

## Why this matters in a bank or fintech

A fee rule that's off by one kobo, applied to ten million transfers, is a ₦100,000 reconciliation break and a regulator's question. Teams can only change money code quickly if a suite of fast tests proves that yesterday's behaviour still holds. Every job description for a Java backend role mentions JUnit, and every capstone in this programme is graded partly on its tests. This topic gets you writing them from week 3; topic 7.1 adds Mockito, test doubles and test-driven development.

## 1. What a unit test is

A **unit test** is a small program that runs one piece of your code (a "unit", usually a method or a class) with known inputs and checks the output automatically. In lesson 0.3 you wrote a home-made `check(...)` method; a testing framework does the same job properly: it finds and runs every test, reports exactly which ones failed and why, and runs in CI on every push.

Good unit tests are **fast** (milliseconds), **independent** (any order, no shared state), **repeatable** (no real clock, network or database) and **self-checking** (pass or fail, no reading output by eye).

The Java standard is **JUnit**. This programme uses JUnit 6, whose API (called *Jupiter*, in `org.junit.jupiter`) is the same one JUnit 5 introduced, so JUnit 5 tutorials and code work unchanged. Alongside it we use **AssertJ** for readable assertions. Both are already in the [Java CLI starter](../../../../templates/java-cli-starter/README.md)'s `pom.xml`.

## 2. Where tests live

Maven keeps tests next to, but separate from, production code:

```
fee-calculator/
├── pom.xml
└── src/
    ├── main/java/ng/itlearning/fees/FeeCalculator.java       production code
    └── test/java/ng/itlearning/fees/FeeCalculatorTest.java   its tests: same package, name ends in Test
```

Test classes use the **same package** as the class they test, so they can see package-private methods. Maven's Surefire plugin runs every class whose name ends in `Test` during `./mvnw test` (and `./mvnw verify`, which CI runs). Test code never goes into the built jar.

## 3. The class under test

A transfer fee: ₦10 up to ₦5,000, ₦25 up to ₦50,000, ₦50 above, each plus 7.5% VAT, rounded half-even to the kobo:

```java
public class FeeCalculator {

    private static final BigDecimal FIVE_THOUSAND = new BigDecimal("5000.00");
    private static final BigDecimal FIFTY_THOUSAND = new BigDecimal("50000.00");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.075");

    public BigDecimal transferFee(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal fee;
        if (amount.compareTo(FIVE_THOUSAND) <= 0) {
            fee = new BigDecimal("10.00");
        } else if (amount.compareTo(FIFTY_THOUSAND) <= 0) {
            fee = new BigDecimal("25.00");
        } else {
            fee = new BigDecimal("50.00");
        }
        return fee.add(fee.multiply(VAT_RATE)).setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

## 4. Your first test

```java
package ng.itlearning.fees;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void chargesTenNairaPlusVatUpToFiveThousand() {
        // Arrange
        BigDecimal amount = new BigDecimal("2000.00");
        // Act
        BigDecimal fee = calculator.transferFee(amount);
        // Assert
        assertThat(fee).isEqualByComparingTo("10.75");
    }
}
```

- `@Test` marks a method JUnit should run. Test classes and methods don't need to be `public`.
- **Arrange–Act–Assert**: set up the inputs, call the code once, check the result. Keeping the three parts visible makes every test easy to read.
- The **name** states the behaviour, so a failure report reads like a broken rule: `chargesTenNairaPlusVatUpToFiveThousand`. Avoid `test1` or `testFee`.
- JUnit creates a **new instance of the test class for every test method**, so the `calculator` field is fresh each time and tests can't affect each other.

**Run it:** click the green ▶ beside the class or method in IntelliJ, or from a terminal:

```bash
./mvnw test                                  # every test
./mvnw test -Dtest=FeeCalculatorTest         # one class
```

## 5. Assertions

AssertJ assertions start with `assertThat(actual)` and read as a sentence:

```java
assertThat(fee).isEqualByComparingTo("10.75");        // BigDecimal: compares the value
assertThat(reference).startsWith("TRF-").hasSize(14);
assertThat(isAllowed).isTrue();
assertThat(statementLines).hasSize(3).contains(salaryLine);
assertThat(balances).containsEntry("0123456789", new BigDecimal("500.00"));
```

You'll also meet JUnit's own assertions in older codebases. Note the order: **expected first, then actual**:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;

assertEquals(new BigDecimal("10.75"), calculator.transferFee(new BigDecimal("2000.00")));
```

**`BigDecimal` trap:** `new BigDecimal("10.75").equals(new BigDecimal("10.750"))` is **false**, because `equals` also compares the scale (the number of decimal places). `assertEquals` uses `equals`, so it fails on a scale difference even when the amounts are the same. For money, compare values with `isEqualByComparingTo` (or `compareTo(...) == 0`), or make sure both sides have the same scale.

## 6. Testing that bad input is rejected

The rules about what's *not* allowed matter as much as the happy path. Check the exception type and, where it's part of the contract, the message:

```java
@Test
void rejectsZeroAmount() {
    assertThatThrownBy(() -> calculator.transferFee(new BigDecimal("0.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Amount must be positive");
}
```

The `() -> ...` is a **lambda**: a piece of code passed to the assertion so it can run it and catch what it throws. Topic 1.7 explains lambdas fully; for now, read it as "run this". The plain-JUnit version is `assertThrows(IllegalArgumentException.class, () -> calculator.transferFee(null))`, which also returns the exception so you can check its message.

## 7. Boundaries and parameterised tests

Bugs live on the edges: `<` instead of `<=`, a band that starts one kobo late. For each rule, test **just below, exactly on, and just above** every boundary. Instead of copying a test five times, use a **parameterised test**:

```java
@ParameterizedTest(name = "₦{0} → fee ₦{1}")
@CsvSource({
        "0.01,     10.75",
        "5000.00,  10.75",
        "5000.01,  26.88",
        "50000.00, 26.88",
        "50000.01, 53.75"
})
void appliesEachBandAtItsBoundaries(String amount, String expectedFee) {
    assertThat(calculator.transferFee(new BigDecimal(amount))).isEqualByComparingTo(expectedFee);
}
```

Each row runs as its own test, and the `name` pattern makes the report readable: `₦5000.01 → fee ₦26.88`. (₦25 plus 7.5% VAT is ₦26.875, which rounds half-even to ₦26.88.) Other sources: `@ValueSource(strings = {"0.00", "-1.00"})` for a single argument, and `@EnumSource` for every value of an enum.

A useful checklist for choosing cases: **zero, one, many; empty and `null`; negative; the smallest and largest valid values; each boundary ±1 kobo.**

## 8. Set-up, display names and other annotations

| Annotation | Use |
| --- | --- |
| `@BeforeEach` | Runs before every test: build fresh test data |
| `@AfterEach` | Runs after every test: rarely needed in unit tests |
| `@DisplayName("rejects zero and negative amounts")` | A readable name in reports |
| `@Disabled("TRF-231: rounding rule pending")` | Skip a test, always with a reason; never leave it for long |
| `@Nested` | Group related tests in an inner class |

```java
private Wallet wallet;

@BeforeEach
void openWalletWithFiveThousandNaira() {
    wallet = new Wallet("0123456789", new BigDecimal("5000.00"));
}
```

## 9. Reading a failing test

Change the expected value in the first test to `"10.00"` and run `./mvnw test`:

```
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in ng.itlearning.fees.FeeCalculatorTest
[ERROR] ng.itlearning.fees.FeeCalculatorTest.chargesTenNairaPlusVatUpToFiveThousand <<< FAILURE!
org.opentest4j.AssertionFailedError:

expected: 10.00
 but was: 10.75
	at ng.itlearning.fees.FeeCalculatorTest.chargesTenNairaPlusVatUpToFiveThousand(FeeCalculatorTest.java:27)
...
[INFO] BUILD FAILURE
```

Read it like any error: **which test** (the method name), **expected versus actual**, and **the line**. A **failure** means an assertion didn't hold; an **error** means the test threw an unexpected exception. Then decide which is wrong: the code or the test's expectation. Never "fix" a failing test by deleting its assertion.

Because CI runs `./mvnw verify`, a failing test turns your GitHub Actions run (topic 1.2) red and blocks the pull request. That's the point: the green tick now means "the fee rules still hold".

## 10. What to test, and what not to

- **Test behaviour, not implementation.** Assert on what a method returns or what it changes, not on how it does it. Then you can rewrite the inside without rewriting the tests.
- **One reason to fail per test.** Several related assertions are fine; testing three unrelated rules in one method is not.
- **Test the rules that matter most first**: money calculations, limits, state changes (frozen, closed), and every rejection.
- **Don't test the language or libraries** (that `ArrayList.add` works), or trivial getters.
- **Keep tests deterministic.** A test that depends on today's date or random numbers will fail one day for no reason. Topic 7.1 shows how to inject a `Clock`.

## Worked example: testing a wallet

```java
class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void openWallet() {
        wallet = new Wallet("0123456789", new BigDecimal("5000.00"));
    }

    @Test
    void debitReducesTheBalance() {
        wallet.debit(new BigDecimal("1200.50"));
        assertThat(wallet.balance()).isEqualByComparingTo("3799.50");
    }

    @Test
    void debitOfTheWholeBalanceLeavesZero() {
        wallet.debit(new BigDecimal("5000.00"));
        assertThat(wallet.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void overdraftIsRejectedAndTheBalanceIsUnchanged() {
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("5000.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.balance()).isEqualByComparingTo("5000.00");
    }

    @Test
    void frozenWalletCannotBeDebited() {
        wallet.freeze();
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

The third test checks two things that belong together: the rejection **and** that nothing changed. A rejected debit that still reduced the balance is the worst kind of bug.

## Common mistakes

- Comparing `BigDecimal` with `equals`/`assertEquals` and getting failures for `10.75` versus `10.750`.
- Testing only the happy path, never the rejections or the boundaries.
- Test names like `test1` that say nothing when they fail.
- Swapping expected and actual in `assertEquals`, which makes failure messages misleading.
- Tests that depend on each other's order or on shared static state.
- Deleting or `@Disabled`-ing a failing test instead of understanding it.

## Practice

1. Add the `FeeCalculator` and its tests to your Module 1 repository and get a green CI run.
2. Write tests for your `isValidAccountNumber` (lesson 0.3) or a NUBAN check: valid, 9 digits, 11 digits, letters, `null`, empty.
3. Add a POS fee rule (0.5%, capped at ₦100) with a parameterised test that covers the cap boundary.
4. Write the `Wallet` class so the worked-example tests pass. Add tests for `credit` and for a statement whose closing balance equals opening plus credits minus debits.
5. Break the fee rule on purpose (`<` instead of `<=`), confirm which test catches it, then fix it.

## Key terms

Unit test, test class, `@Test`, Arrange–Act–Assert, assertion, AssertJ, `isEqualByComparingTo`, expected versus actual, `assertThrows`, `assertThatThrownBy`, parameterised test, `@CsvSource`, `@ValueSource`, `@BeforeEach`, boundary value, failure versus error, Surefire, test report.

## Further reading

- [JUnit User Guide](https://docs.junit.org/current/user-guide/)
- [AssertJ: core assertions guide](https://assertj.github.io/doc/#assertj-core-assertions-guide)
- [IntelliJ IDEA: JUnit](https://www.jetbrains.com/help/idea/junit.html)
- [Maven Surefire plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [IntelliJ IDEA: Run tests](https://www.jetbrains.com/help/idea/performing-tests.html)
