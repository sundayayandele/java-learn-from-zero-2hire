## What you will be able to do

- Write clear, independent unit tests with **JUnit 5** (Jupiter) and **AssertJ**: naming, arrange–act–assert, parameterised tests, nested tests and exception assertions.
- Choose the right **test double** (dummy, stub, fake, mock, spy) and use **Mockito** without over-mocking.
- Control time and randomness (`Clock`, seeded generators) so tests are deterministic.
- Practise **test-driven development**: red, green, refactor.
- Recognise and fix **flaky**, brittle and slow tests.

## Why this matters in a bank or fintech

Money code must be correct at the boundaries: exactly ₦5,000.00 versus ₦5,000.01, the last day of the month, a leap year, a refund larger than the original payment. Unit tests are where you prove those rules cheaply, thousands of times per build. Banks treat test suites as evidence: auditors, change-approval boards and your future colleagues read them to understand what the system guarantees. In interviews, "how would you test this?" follows almost every coding question.

## 1. JUnit 5 essentials

Spring Boot's test starter brings JUnit Jupiter, AssertJ, Mockito and Hamcrest.

```java
class FeeCalculatorTest {

    private final FeeCalculator fees = new FeeCalculator(FeeSchedule.standard());

    @Test
    void chargesFlatFeeUpToFiveThousandNaira() {
        // arrange
        Money amount = Money.ngn("5000.00");
        // act
        Money fee = fees.transferFee(amount);
        // assert
        assertThat(fee).isEqualTo(Money.ngn("10.75"));
    }

    @ParameterizedTest(name = "{0} → fee {1}")
    @CsvSource({
            "0.01,      10.75",
            "5000.00,   10.75",
            "5000.01,   26.88",
            "50000.00,  26.88",
            "50000.01,  53.75"
    })
    void appliesTierBoundaries(String amount, String expectedFee) {
        assertThat(fees.transferFee(Money.ngn(amount))).isEqualTo(Money.ngn(expectedFee));
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        assertThatThrownBy(() -> fees.transferFee(Money.ngn("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Nested
    class StaffAccounts {
        @Test
        void areNotCharged() {
            assertThat(fees.transferFee(Money.ngn("10000.00"), CustomerType.STAFF)).isEqualTo(Money.ngn("0.00"));
        }
    }
}
```

Good unit tests are **FIRST**: Fast (milliseconds), Independent (no order dependence, no shared mutable state), Repeatable (same result every time, anywhere), Self-validating (pass or fail, no reading logs), Timely (written with or before the code).

Name tests after **behaviour**, not methods: `appliesTierBoundaries` says more than `testTransferFee2`. One test, one reason to fail.

### AssertJ

AssertJ reads like a sentence and gives precise failure messages:

```java
assertThat(statement.lines())
        .hasSize(3)
        .extracting(StatementLine::narration)
        .containsExactly("Opening balance", "POS NG LAGOS", "Transfer to 0123456789");

assertThat(result).isInstanceOfSatisfying(TransferResult.Rejected.class,
        r -> assertThat(r.reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));

assertThat(balance).isEqualByComparingTo("1250.00");   // BigDecimal: compares value, ignores scale
```

Use `isEqualByComparingTo` for `BigDecimal`: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is `false`.

## 2. Test doubles and Mockito

A unit test isolates the code under test from slow or uncontrollable collaborators.

| Double | What it does | Example |
| --- | --- | --- |
| Dummy | Fills a parameter, never used | `null` or a placeholder object |
| Stub | Returns canned answers | An exchange-rate provider returning 1,580.00 |
| Fake | Working lightweight implementation | An in-memory `AccountRepository` backed by a `HashMap` |
| Mock | Records calls so you can verify them | Verify that `notifier.sendDebitAlert(...)` was called once |
| Spy | Wraps a real object, overriding some calls | Rarely needed; usually a design smell |

Mockito creates stubs and mocks:

```java
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock AccountRepository accounts;
    @Mock DebitAlertNotifier notifier;
    Clock clock = Clock.fixed(Instant.parse("2026-10-24T09:00:00Z"), ZoneId.of("Africa/Lagos"));

    TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, notifier, new FeeCalculator(FeeSchedule.standard()), clock);
    }

    @Test
    void debitsSourceCreditsDestinationAndAlertsCustomer() {
        Account source = Account.open("0123456789", Money.ngn("10000.00"));
        Account destination = Account.open("9876543210", Money.ngn("0.00"));
        when(accounts.lockBoth("0123456789", "9876543210")).thenReturn(new AccountPair(source, destination));

        service.transfer(new TransferCommand("0123456789", "9876543210", Money.ngn("2500.00"), "ref-1"));

        assertThat(source.balance()).isEqualTo(Money.ngn("7489.25"));       // 2,500 + 10.75 fee
        assertThat(destination.balance()).isEqualTo(Money.ngn("2500.00"));
        verify(notifier).sendDebitAlert(eq("0123456789"), eq(Money.ngn("2510.75")), any());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void doesNotAlertWhenFundsAreInsufficient() {
        when(accounts.lockBoth(any(), any())).thenReturn(new AccountPair(
                Account.open("0123456789", Money.ngn("100.00")), Account.open("9876543210", Money.ngn("0.00"))));

        assertThatThrownBy(() -> service.transfer(new TransferCommand("0123456789", "9876543210", Money.ngn("2500.00"), "ref-2")))
                .isInstanceOf(InsufficientFundsException.class);
        verifyNoInteractions(notifier);
    }
}
```

Useful Mockito tools: `ArgumentCaptor` to inspect what was passed, `thenThrow` to simulate failures, `inOrder(...)` when order matters, strict stubs (the default with `MockitoExtension`) which fail on unused stubbing.

### Mock less

- **Mock at boundaries** you do not own or that are slow: repositories, HTTP clients, message publishers, clocks. Do **not** mock value objects (`Money`), simple collaborators, or the class under test.
- **Prefer state over interaction:** assert balances and results; verify calls only when the call *is* the behaviour (an alert must be sent).
- **Do not mock what you do not own** without a contract test somewhere else: a mocked `RestClient` says nothing about the real provider (use WireMock, topic 5.4).
- If a test needs ten mocks, the class has too many responsibilities.

## 3. Deterministic time and randomness

Code that calls `LocalDate.now()` or `UUID.randomUUID()` directly cannot be tested at month-end or with known IDs. Inject a `Clock` (Phase 1 already did this) and an ID generator:

```java
class InterestAccrual {
    private final Clock clock;
    InterestAccrual(Clock clock) { this.clock = clock; }

    Money dailyInterest(Account account) {
        LocalDate today = LocalDate.now(clock);
        int daysInYear = today.isLeapYear() ? 366 : 365;
        ...
    }
}

@Test
void usesThreeHundredSixtySixDaysInALeapYear() {
    Clock leapDay = Clock.fixed(Instant.parse("2028-02-29T12:00:00Z"), ZoneOffset.UTC);
    assertThat(new InterestAccrual(leapDay).dailyInterest(savings("1000000.00", "0.10")))
            .isEqualTo(Money.ngn("273.22"));
}
```

## 4. Test-driven development

TDD is a short loop:

1. **Red:** write a small test for the next behaviour; run it; watch it fail (for the right reason).
2. **Green:** write the simplest code that passes.
3. **Refactor:** clean up names, duplication and design while all tests stay green.

A few cycles on a VAT-on-fees rule:

```java
// Red 1
@Test void addsVatAtSevenAndAHalfPercentToTheFee() {
    assertThat(fees.totalCharge(Money.ngn("5000.00"))).isEqualTo(Money.ngn("11.56"));   // 10.75 + 0.81 VAT
}
// Green 1: totalCharge = fee + fee × 0.075, rounded HALF_EVEN
// Red 2
@Test void staffPayNoFeeAndThereforeNoVat() { ... }
// Green 2, then Refactor: extract a ChargeBreakdown record (fee, vat, total) because both tests needed the parts
```

Why teams use it: the tests describe behaviour before implementation details exist, design pressure pushes towards small, injectable collaborators, and you are never far from a green state. It works especially well for rule-heavy domain logic (fees, interest, limits, eligibility). It is less natural for exploratory or UI work; there, write tests immediately after.

## 5. Flaky, brittle and slow tests

- **Flaky** (sometimes fails): shared state between tests, real time (`Thread.sleep`, `now()`), random data without a seed, test order dependence, concurrency without proper synchronisation, real network calls. Fix the cause; never just rerun.
- **Brittle** (breaks on harmless refactors): verifying every internal call, asserting on log text or exact `toString` output, mocking private details.
- **Slow:** starting Spring for logic that needs no Spring. A plain unit test should not use `@SpringBootTest`. Keep the default `mvn test` fast; run integration tests in a separate phase (`mvn verify` with the Failsafe plugin), topic 7.2.

Test data builders keep tests readable as models grow:

```java
Account account = anAccount().withNumber("0123456789").withBalance("10000.00").frozen().build();
```

## 6. Test data builders

Tests become unreadable when every one of them builds a nine-field `Transfer` by hand, and fragile when a new required field breaks 200 constructors. A **test data builder** gives sensible defaults and lets each test state only what matters to it:

```java
class TransferBuilder {
    private String from = "0123456789";
    private String to = "0987654321";
    private Money amount = Money.of("10000.00", "NGN");
    private Channel channel = Channel.NIP;
    private Instant at = Instant.parse("2026-10-01T09:00:00Z");

    static TransferBuilder aTransfer() { return new TransferBuilder(); }
    TransferBuilder amount(String value) { this.amount = Money.of(value, "NGN"); return this; }
    TransferBuilder channel(Channel c) { this.channel = c; return this; }
    TransferBuilder at(String instant) { this.at = Instant.parse(instant); return this; }
    Transfer build() { return new Transfer(from, to, amount, channel, at); }
}

@Test
void ussdTransfersPayTheFlatFee() {
    var t = aTransfer().channel(Channel.USSD).build();     // only what this test cares about
    assertThat(fees.feeFor(t)).isEqualByComparingTo("6.98");
}
```

Keep builders next to the tests (`src/test/java`), give every default a valid business value, and add one method per field a test needs to vary. An **Object Mother** (`Transfers.largeNipTransfer()`) is a lighter alternative for a few named, common cases.

## 7. Testing asynchronous code with Awaitility

Code that finishes later (an `@Async` method, an event listener after commit, a scheduled job, a consumer) tempts people into `Thread.sleep(2000)`: slow when the work is fast, and flaky when the machine is slow. **Awaitility** (managed by Spring Boot, so no version is needed) polls until a condition holds or a timeout expires:

```java
import static org.awaitility.Awaitility.await;

@Test
void smsIsSentAfterTheTransferCommits() {
    transferService.transfer(request);
    await().atMost(Duration.ofSeconds(5))
           .untilAsserted(() -> assertThat(fakeSms.sentTo()).contains("+2348012345678"));
}
```

Prefer making code synchronous in unit tests (inject an `Executor` and pass a direct executor `Runnable::run` in tests) and keep Awaitility for integration tests where real threads are involved.

## Common mistakes

- Tests with no assertions, or that only check "no exception".
- `assertEquals` on `BigDecimal` values with different scales.
- Mocking everything, including value objects and the class under test.
- `@SpringBootTest` for pure logic, making the suite slow.
- Tests depending on each other's data or execution order.
- `Thread.sleep` to wait for asynchronous work (use Awaitility in integration tests).
- Writing tests after the release "when there is time".

## Practice

1. Write parameterised boundary tests for your Phase 1 fee schedule and interest rules, including leap years and month-ends with a fixed `Clock`.
2. Refactor one over-mocked test in your capstone into a state-based test with a fake repository.
3. Do project 7.B (fee and tax engine kata) test-first, committing after each red, green and refactor step.
4. Introduce a deliberate flaky test (using `LocalDateTime.now()`), observe it, and fix it with an injected `Clock`.
5. Write a `TransferBuilder` and convert five existing tests to use it; count how many lines each test lost.
6. Replace every `Thread.sleep` in your test suite with Awaitility or a synchronous executor, and measure the suite's run time before and after.

## Key terms

Unit test, JUnit Jupiter, `@Test`, `@ParameterizedTest`, `@CsvSource`, `@Nested`, `@BeforeEach`, AssertJ, `isEqualByComparingTo`, arrange–act–assert, FIRST, test double, dummy, stub, fake, mock, spy, Mockito, `@Mock`, `when/thenReturn`, `verify`, `ArgumentCaptor`, strict stubs, state-based vs interaction-based testing, `Clock`, test-driven development, red–green–refactor, flaky test, brittle test, test data builder, test data builder, Object Mother, Awaitility, polling assertion.

## Further reading

- [JUnit 5 User Guide](https://docs.junit.org/current/user-guide/)
- [AssertJ documentation: Core assertions guide](https://assertj.github.io/doc/)
- [Mockito documentation (Javadoc)](https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html)
- [Martin Fowler: Mocks Aren't Stubs](https://martinfowler.com/articles/mocksArentStubs.html)
- [Martin Fowler: Test-Driven Development](https://martinfowler.com/bliki/TestDrivenDevelopment.html)
- [Spring Boot reference: Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
