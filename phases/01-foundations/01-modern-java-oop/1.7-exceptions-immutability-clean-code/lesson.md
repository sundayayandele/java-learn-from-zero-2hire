## What you will be able to do

- Explain the exception hierarchy and choose between checked and unchecked exceptions.
- Throw, catch, wrap and translate exceptions without losing information.
- Release resources reliably with try-with-resources.
- Design domain exceptions that carry useful context and are safe to log.
- Build immutable classes and explain why immutability makes code safer.
- Apply everyday clean-code habits: naming, small functions, guard clauses, no magic numbers, comments that explain *why*.

## Why this matters in a bank or fintech

When a transfer fails, the customer, the support agent, the auditor and the on-call engineer all need a precise, honest answer: was the money debited? Swallowed exceptions, vague `catch (Exception e) {}` blocks and log messages like "error occurred" are how banks end up with debited customers and no record of why. Immutability prevents a different class of incident: a shared object changed by one part of the code while another is still using it. Clean code is what lets the next engineer fix all of this at 3 a.m.

## 1. The exception hierarchy

```text
Throwable
 ├── Error                    serious JVM problems: OutOfMemoryError, StackOverflowError. Don't catch.
 └── Exception
      ├── IOException, SQLException, ...     CHECKED: the compiler forces you to handle or declare them
      └── RuntimeException                   UNCHECKED: no compile-time obligation
           ├── NullPointerException
           ├── IllegalArgumentException
           ├── IllegalStateException
           ├── ArithmeticException
           └── your domain exceptions (usually)
```

**Checked exceptions** must be caught or declared with `throws` in the method signature. They model failures the caller can reasonably be expected to recover from, typically I/O. **Unchecked exceptions** model programming errors (a null where none should be) or business rule violations that bubble up to a central handler.

Modern Java practice, and Spring's in particular, leans towards unchecked exceptions for application code: they do not pollute every signature, and they work with lambdas, which cannot throw checked exceptions. Use checked exceptions sparingly, for recoverable conditions at clear boundaries.

## 2. Throwing and catching

```java
public void withdraw(Money amount) {
    if (amount.amount().signum() <= 0) {
        throw new IllegalArgumentException("Withdrawal must be positive: " + amount);
    }
    if (status != Status.ACTIVE) {
        throw new IllegalStateException("Account " + number + " is " + status);
    }
    if (balance.amount().compareTo(amount.amount()) < 0) {
        throw new InsufficientFundsException(number, amount, balance);
    }
    balance = balance.minus(amount);
}
```

Standard exceptions have meanings that reviewers expect: `IllegalArgumentException` for a bad argument, `IllegalStateException` when the object is in the wrong state for the call, `NullPointerException` (via `Objects.requireNonNull`) for forbidden nulls, `UnsupportedOperationException` for operations an implementation does not support.

```java
try {
    gateway.charge(card, amount);
} catch (GatewayTimeoutException e) {          // most specific first
    retryQueue.add(payment);
} catch (GatewayDeclinedException | CardExpiredException e) {   // multi-catch
    payment.markFailed(e.getMessage());
} finally {
    metrics.recordAttempt();                   // runs whether or not an exception occurred
}
```

### Catch only what you can handle

The rule: catch an exception only if you can do something meaningful (retry, fall back, translate it to a better exception, or report it at a boundary). Otherwise, let it propagate.

```java
// Anti-pattern 1: swallowing. The transfer silently does nothing.
try { ledger.post(entry); } catch (Exception e) { }

// Anti-pattern 2: log and rethrow. The same error is logged five times as it bubbles up.
catch (SQLException e) { log.error("failed", e); throw new RuntimeException(e); }

// Anti-pattern 3: losing the cause. The root SQL error is gone forever.
catch (SQLException e) { throw new LedgerException("Posting failed"); }

// Correct: translate, keep the cause, add context, log once at the boundary.
catch (SQLException e) {
    throw new LedgerException("Posting failed for entry " + entry.id(), e);   // e becomes getCause()
}
```

**Exception translation** keeps layers clean: the service layer should throw `LedgerException`, not leak `SQLException` from the database driver to the web layer.

## 3. try-with-resources

Anything that holds a connection, file handle or socket must be closed, even when an exception is thrown. Any class implementing `AutoCloseable` can be used in try-with-resources, which closes resources automatically in reverse order:

```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account WHERE number = ?")) {
    ps.setString(1, number);
    try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getBigDecimal(1) : null;
    }
}   // rs, ps and conn are closed here, whatever happened
```

Before Java 7 this needed nested `finally` blocks that were easy to get wrong, and leaked connections are still a classic cause of production outages ("connection pool exhausted"). If closing itself throws while another exception is in flight, the close failure is attached as a **suppressed** exception (`e.getSuppressed()`) rather than hiding the original.

## 4. Designing domain exceptions

```java
public class InsufficientFundsException extends RuntimeException {
    private final String accountNumber;
    private final Money requested;
    private final Money available;

    public InsufficientFundsException(String accountNumber, Money requested, Money available) {
        super("Insufficient funds in account %s: requested %s, available %s"
                .formatted(mask(accountNumber), requested, available));
        this.accountNumber = accountNumber;
        this.requested = requested;
        this.available = available;
    }

    public Money requested() { return requested; }
    public Money available() { return available; }

    private static String mask(String acc) { return "******" + acc.substring(acc.length() - 4); }
}
```

Good domain exceptions:

- have a name that states the business problem (`InsufficientFundsException`, `DailyLimitExceededException`, `AccountFrozenException`);
- carry structured context as fields so a handler can turn them into an HTTP response or an error code (Module 4 maps them to HTTP 422 with Problem Details);
- have messages that are safe to log: **mask account numbers and never include PINs, passwords, full card numbers or tokens**;
- are usually unchecked, sometimes grouped under a base class (`BankingException`) so a handler can catch the family.

Do not use exceptions for normal control flow. "Account not found" for a lookup that is expected to miss is better expressed with `Optional` (topic 1.5) or a sealed result type (topic 1.6). Exceptions are for the exceptional, and creating one is relatively expensive because it captures a stack trace.

## 5. Immutability

An immutable object's state cannot change after construction. `String`, `BigDecimal`, `LocalDate` and records are immutable.

### How to make a class immutable

1. Make the class `final` (or use a record) so subclasses cannot add mutable state.
2. Make every field `private final`.
3. Set every field in the constructor and validate there.
4. Provide no setters; "modifying" methods return a new instance.
5. Defensively copy mutable inputs and outputs (lists, arrays, `Date`).

```java
public final class ExchangeRate {
    private final Currency base;
    private final Currency quote;
    private final BigDecimal rate;
    private final LocalDate asOf;

    public ExchangeRate(Currency base, Currency quote, BigDecimal rate, LocalDate asOf) {
        if (rate.signum() <= 0) throw new IllegalArgumentException("rate must be positive");
        this.base = base; this.quote = quote; this.rate = rate; this.asOf = asOf;
    }

    public Money convert(Money m) {
        if (!m.currency().equals(base)) throw new IllegalArgumentException("Expected " + base);
        return new Money(m.amount().multiply(rate).setScale(2, RoundingMode.HALF_EVEN), quote);
    }

    public ExchangeRate inverse() {
        return new ExchangeRate(quote, base, BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_EVEN), asOf);
    }
}
```

(As a record this would be four lines plus the validation. Use a class when you need to hide fields or control the API more tightly.)

### Why immutability pays off

- **Thread safety for free.** Immutable objects can be shared across threads with no locks (Module 2).
- **Safe sharing and caching.** An exchange rate handed to ten services cannot be changed by one of them.
- **Valid for life.** Validated once in the constructor, always valid afterwards.
- **Safe map keys and set members** (topic 1.4).
- **Easier reasoning and testing.** No hidden state changes between two lines of code.

The cost is creating new objects for every change, which the JVM handles cheaply for short-lived objects (topic 2.4). Keep mutable state where it genuinely belongs (an entity's lifecycle, a builder, a performance-critical buffer) and make everything else immutable by default. Also prefer `final` local variables and parameters in long methods: they tell the reader "this never changes".

### Date and time: use `java.time`

The old `java.util.Date` and `Calendar` are mutable and confusing. Always use the immutable `java.time` types: `LocalDate` (value date), `LocalDateTime` (no zone), `Instant` (a point on the timeline, for timestamps), `ZonedDateTime` with `ZoneId.of("Africa/Lagos")`, `Duration` and `Period`. Store timestamps as `Instant` in UTC and convert to local time only for display.

## 6. Clean-code habits

**Names reveal intent.**

```java
int d;                                 // elapsed days? days until due? a discount?
int daysPastDue;                       // clear
List<Account> list1;                   // meaningless
List<Account> dormantAccounts;         // clear
boolean flag;  →  boolean isOverdrawn;
void process(); →  void postEndOfDayInterest();
```

**Small functions that do one thing**, at one level of abstraction. If you need "and" to describe a method, it probably does two things.

**Guard clauses instead of deep nesting.**

```java
// Arrow-shaped code
if (account != null) {
    if (account.isActive()) {
        if (amount.signum() > 0) { ... }
    }
}

// Guard clauses: fail fast, then the happy path at the left margin
if (account == null) throw new AccountNotFoundException(number);
if (!account.isActive()) throw new AccountInactiveException(number);
if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
...
```

**No magic numbers.**

```java
if (amount.compareTo(new BigDecimal("5000000")) > 0)           // why five million?
private static final BigDecimal CTR_THRESHOLD_INDIVIDUAL = new BigDecimal("5000000");  // name the rule
if (amount.compareTo(CTR_THRESHOLD_INDIVIDUAL) > 0) reportCashTransaction(tx);
```

**Comments explain why, not what.** `// increment i` is noise. `// CBN requires reversal within 24h; we use 23h to leave a buffer for the batch window` is gold. If you need a comment to explain *what* code does, try renaming and extracting first.

**Other habits reviewers look for:** no commented-out code (Git remembers it); no unused imports or variables; consistent formatting (let the IDE or a formatter do it); methods under about 20 lines; classes under a few hundred; tests named after behaviour (`withdraw_rejectsAmountAboveBalance`). Leave code a little cleaner than you found it (the "boy scout rule"), but do big refactors in their own pull request.

## Worked example: a transfer with honest failure handling

```java
public TransferReceipt transfer(TransferRequest req) {
    Objects.requireNonNull(req, "request");
    Account source = accounts.findByNumber(req.from())
            .orElseThrow(() -> new AccountNotFoundException(req.from()));
    Account target = accounts.findByNumber(req.to())
            .orElseThrow(() -> new AccountNotFoundException(req.to()));

    limits.check(source, req.amount());          // throws DailyLimitExceededException
    source.withdraw(req.amount());               // throws InsufficientFundsException
    target.deposit(req.amount());

    try {
        ledger.record(source, target, req.amount(), req.reference());
    } catch (LedgerUnavailableException e) {
        throw new TransferFailedException(req.reference(), "ledger unavailable", e);  // keep the cause
    }
    return new TransferReceipt(req.reference(), Instant.now(), req.amount());
}
```

Each failure has a specific, named exception with context, causes are preserved, nothing is swallowed, and logging happens once in the web layer's handler (Module 4). In Module 3 a database transaction will make sure the withdraw and deposit either both happen or neither does.

## Common mistakes

- `catch (Exception e) {}` or `e.printStackTrace()` in production code.
- Throwing a new exception without passing the original as the cause.
- Logging the same exception at every layer.
- Exception messages containing PINs, tokens, full PANs or passwords.
- Closing resources manually in `finally` (or not at all) instead of try-with-resources.
- "Immutable" classes with a getter returning an internal mutable list.
- Using `java.util.Date` in new code.

## Practice

1. Create `BankingException` and three subclasses with structured fields. Write a small `main` that triggers each and prints `getMessage()` and `getCause()`.
2. Write a method that reads a CSV statement file line by line with `Files.newBufferedReader` in try-with-resources, and translates `IOException` into a `StatementImportException` that includes the file name and line number.
3. Make `ExchangeRate` above a record with the same validation. Then write a test proving that `convert` and `inverse` round-trip within one kobo.
4. Take any 40-line method you wrote in topics 1.2–1.6, rename unclear variables, extract two methods, replace nesting with guard clauses, and name every magic number.

## Key terms

Checked exception, unchecked exception, `Error`, `throws`, multi-catch, `finally`, try-with-resources, `AutoCloseable`, suppressed exception, exception chaining (cause), exception translation, domain exception, immutability, defensive copy, `java.time`, guard clause, magic number.

## Further reading

- [The Java Tutorials: Exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/index.html)
- [The Java Tutorials: The try-with-resources Statement](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)
- [The Java Tutorials: Immutable Objects](https://docs.oracle.com/javase/tutorial/essential/concurrency/immutable.html)
- [The Java Tutorials: Date-Time](https://docs.oracle.com/javase/tutorial/datetime/index.html)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- Robert C. Martin, *Clean Code*, chapters 2–4 and 7 (book)
- [dev.java (Oracle): Exceptions](https://dev.java/learn/language/annotations-exceptions/exceptions/)
- [dev.java (Oracle): Date and Time](https://dev.java/learn/api/date-time-regex/date-time/)
