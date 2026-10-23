## What you will be able to do

- Model immutable data with **records**, including validation in compact constructors.
- Close a type hierarchy with **sealed** classes and interfaces.
- Use **pattern matching** for `instanceof`, record patterns and `switch`, with guards and exhaustiveness checks.
- Write multi-line strings with **text blocks**.
- Combine these features into "data-oriented" domain models that the compiler checks for you.

## Why this matters in a bank or fintech

Financial domains are full of closed sets of cases: a payment is a card payment, a bank transfer or a wallet top-up; a transaction outcome is approved, declined or pending review. When a new case is added, every place that handles the set must change, and missing one is a production bug. Java 21's records, sealed types and pattern matching let the compiler find every one of those places for you. Job postings that ask for "Java 21+" are asking whether you write code this way.

## 1. Records

A record is a class whose purpose is to carry data. You declare the components; the compiler generates the constructor, private final fields, accessors, `equals`, `hashCode` and `toString`.

```java
public record Money(BigDecimal amount, Currency currency) { }

Money m = new Money(new BigDecimal("1500.00"), Currency.getInstance("NGN"));
m.amount();       // accessor is amount(), not getAmount()
m.equals(new Money(new BigDecimal("1500.00"), Currency.getInstance("NGN")));   // true: value equality
m.toString();     // Money[amount=1500.00, currency=NGN]
```

That one line replaces about 50 lines of hand-written class, and it cannot get `equals`/`hashCode` wrong.

### Validation in a compact constructor

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {                                       // compact constructor: no parameter list
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException("Too many decimal places for " + currency);
        }
    }

    public static Money naira(String value) {            // static factory
        return new Money(new BigDecimal(value), Currency.getInstance("NGN"));
    }

    public Money plus(Money other) {                     // records can have methods
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);   // return a new value
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(currency + " vs " + other.currency);
        }
    }
}
```

What records can and cannot do:

- Fields are final; there are no setters. "Changing" a record means creating a new one (a "wither" such as `withAmount(...)` if you need it).
- They can have static fields and methods, instance methods, nested types, and implement interfaces.
- They cannot extend another class (they implicitly extend `java.lang.Record`) and cannot declare extra instance fields.
- Immutability is **shallow**: a record holding a `List` should copy it defensively (`this.items = List.copyOf(items)` in the compact constructor).

Use records for value objects (`Money`, `AccountNumber`, `DateRange`), DTOs (request/response bodies), events (`TransferCompleted`) and map keys. Do not use them for JPA entities, which need mutable state and a no-argument constructor (Module 4).

## 2. Sealed classes and interfaces

A sealed type lists exactly which classes may extend or implement it.

```java
public sealed interface PaymentMethod permits Card, BankTransfer, Wallet { }

public record Card(String token, String last4, YearMonth expiry) implements PaymentMethod { }
public record BankTransfer(String bankCode, String accountNumber) implements PaymentMethod { }
public record Wallet(String walletId, String provider) implements PaymentMethod { }
```

Each permitted subclass must be `final` (records are), `sealed` (with its own `permits`), or `non-sealed` (open again for extension). Permitted subclasses must live in the same module, or in the same package if you are not using modules.

Sealing does two things. It documents intent: "these are all the payment methods, by design". And it lets the compiler **know the full list**, which makes exhaustive `switch` possible.

Inheritance in topic 1.3 was "open": anyone can add a subclass. Sealed hierarchies are "closed", and the right choice when the business defines a fixed set of variants.

## 3. Pattern matching

### `instanceof` patterns (Java 16)

```java
// Old style: test, then cast
if (method instanceof Card) {
    Card c = (Card) method;
    charge(c.token());
}

// Pattern matching: test and bind in one step
if (method instanceof Card c && c.expiry().isAfter(YearMonth.now())) {
    charge(c.token());
}
```

### Record patterns (Java 21)

A record pattern takes a record apart into its components, and patterns can nest:

```java
if (method instanceof BankTransfer(String bankCode, String acct)) {
    validateNuban(bankCode, acct);
}

record Transfer(Money amount, PaymentMethod from, PaymentMethod to) { }

if (t instanceof Transfer(Money(var amt, var ccy), Wallet w, BankTransfer b)) {
    // amt, ccy, w and b are all in scope
}
```

### Pattern matching for `switch` (Java 21)

`switch` can now match on types and record shapes, with **guards** (`when`) and full exhaustiveness checking:

```java
BigDecimal processingFee(PaymentMethod method, Money amount) {
    return switch (method) {
        case Card c when c.expiry().isBefore(YearMonth.now()) ->
                throw new CardExpiredException(c.last4());
        case Card c -> amount.amount().multiply(new BigDecimal("0.015"))
                .min(new BigDecimal("2000.00"));
        case BankTransfer(var bank, var acct) when bank.equals(OUR_BANK_CODE) -> BigDecimal.ZERO;
        case BankTransfer b -> new BigDecimal("10.75");
        case Wallet w -> new BigDecimal("5.00");
    };   // no default: the compiler knows PaymentMethod has exactly these three subtypes
}
```

Now add `record Ussd(String phone) implements PaymentMethod` to the `permits` list. **Every** switch over `PaymentMethod` without a default stops compiling until someone decides what the USSD fee is. That is the payoff: a business change becomes a compiler-guided checklist instead of a production incident.

Details worth knowing:

- Cases are checked top to bottom. A more specific case (`Card c when ...`) must come before a general one (`Card c`), or the compiler reports it as dominated.
- `case null ->` handles null explicitly; without it, a null selector throws `NullPointerException` as before.
- Adding `default` switches off exhaustiveness checking for future subtypes. Avoid it with sealed hierarchies.
- Java 22 made the unnamed pattern `_` final (`case BankTransfer(var bank, _) -> ...`); in Java 21 it is a preview feature, so name every variable there.

### Modelling outcomes, not just inputs

Sealed records are just as useful for results, replacing booleans and error codes:

```java
public sealed interface AuthorisationResult
        permits Approved, Declined, ReferToIssuer { }
public record Approved(String authCode) implements AuthorisationResult { }
public record Declined(String reason) implements AuthorisationResult { }
public record ReferToIssuer(String phone) implements AuthorisationResult { }

String message = switch (authorise(request)) {
    case Approved a -> "Approved, code " + a.authCode();
    case Declined d -> "Declined: " + d.reason();
    case ReferToIssuer r -> "Call your bank on " + r.phone();
};
```

This style, plain data in records plus behaviour in exhaustive switches, is often called **data-oriented programming**. It complements, rather than replaces, the object-oriented design from topic 1.3: use polymorphic methods when each subtype owns its behaviour, and pattern matching when an operation sits outside the types (reports, fees, serialisation).

## 4. Text blocks

Text blocks (Java 15) are multi-line string literals delimited by `"""`. They make embedded SQL, JSON and messages readable:

```java
String sql = """
        SELECT a.number, a.balance, c.full_name
        FROM account a
        JOIN customer c ON c.id = a.customer_id
        WHERE a.status = 'ACTIVE'
          AND a.balance < ?
        """;

String webhook = """
        {
          "event": "transfer.completed",
          "reference": "%s",
          "amount": %s
        }
        """.formatted(reference, amount);
```

Rules:

- The opening `"""` must be followed by a line break.
- Indentation is stripped up to the leftmost line, *including* the line with the closing `"""`. Move the closing delimiter left to keep indentation in the string.
- A `\` at the end of a line joins it with the next (no newline); `\s` keeps a trailing space that would otherwise be stripped.
- Use `.formatted(...)` for placeholders. **Never** build SQL with string formatting from user input: use `?` parameters (topic 3.5).

## 5. Other modern features you will meet

- **Switch expressions** (Java 14): covered in topic 1.2.
- **`var`** (Java 10): covered in topic 1.2.
- **Helpful NullPointerExceptions** (Java 14): messages now name the null expression, e.g. `Cannot invoke "Account.balance()" because "acc" is null`.
- **Sequenced collections** (Java 21): covered in topic 1.4.
- **Virtual threads** (Java 21): Module 2.
- In **Java 25**: unnamed variables and patterns are long final, module import declarations (`import module java.base;`), flexible constructor bodies (statements before `super(...)`), compact source files with instance `main` methods for small programs, and primitive types in patterns still in preview. None are required for this programme, but you should recognise them in newer codebases.

## Worked example: a fee engine with no `if` chains

```java
public sealed interface Txn permits CardTxn, TransferTxn, AirtimeTxn { Money amount(); }
public record CardTxn(Money amount, String mcc, boolean international) implements Txn { }
public record TransferTxn(Money amount, String destinationBank) implements Txn { }
public record AirtimeTxn(Money amount, String network) implements Txn { }

public final class FeeEngine {
    private static final BigDecimal CAP = new BigDecimal("2000.00");

    public Money fee(Txn txn) {
        BigDecimal fee = switch (txn) {
            case CardTxn c when c.international() -> pct(c.amount(), "0.030");
            case CardTxn c -> pct(c.amount(), "0.015").min(CAP);
            case TransferTxn t when t.amount().amount().compareTo(new BigDecimal("5000")) <= 0 -> new BigDecimal("10.75");
            case TransferTxn t when t.amount().amount().compareTo(new BigDecimal("50000")) <= 0 -> new BigDecimal("26.88");
            case TransferTxn t -> new BigDecimal("53.75");
            case AirtimeTxn a -> BigDecimal.ZERO;
        };
        return new Money(fee.setScale(2, RoundingMode.HALF_EVEN), txn.amount().currency());
    }

    private static BigDecimal pct(Money m, String rate) {
        return m.amount().multiply(new BigDecimal(rate));
    }
}
```

The transfer bands mirror the familiar Nigerian NIP-style fee tiers; treat the numbers as illustrative, since each bank publishes its own.

## Common mistakes

- Calling `money.getAmount()`: record accessors have no `get` prefix.
- Assuming records are deeply immutable when they hold mutable lists or arrays.
- Putting a `default` branch in a switch over a sealed type, which silently swallows new variants.
- Ordering a general case before a guarded specific one.
- Using records as JPA entities.
- Building SQL from text blocks with `formatted(userInput)`.

## Practice

1. Write `record AccountNumber(String value)` that rejects anything but 10 digits, and `record DateRange(LocalDate from, LocalDate to)` that rejects `from` after `to`. Add `boolean contains(LocalDate)`.
2. Model `sealed interface LoanDecision permits Approved, Rejected, ManualReview` with sensible components, and write an exhaustive switch that produces the customer SMS text.
3. Add a fourth `Txn` type, `BillPaymentTxn`, to the worked example. Note every compile error, fix them, and explain why this is better than a runtime failure.
4. Rewrite a 15-line JSON string built with `+` and `\n` as a text block with `formatted`.

## Key terms

Record, component, compact constructor, canonical constructor, value equality, sealed, `permits`, `non-sealed`, pattern matching, type pattern, record pattern, guard (`when`), exhaustiveness, dominance, data-oriented programming, text block.

## Further reading

- [JEP 395: Records](https://openjdk.org/jeps/395)
- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)
- [JEP 440: Record Patterns](https://openjdk.org/jeps/440)
- [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)
- [JEP 378: Text Blocks](https://openjdk.org/jeps/378)
- [Brian Goetz: Data-Oriented Programming in Java (InfoQ)](https://www.infoq.com/articles/data-oriented-programming-java/)
- [dev.java (Oracle): Records](https://dev.java/learn/language/oop/records/)
- [Oracle: Java SE 21 language changes](https://docs.oracle.com/en/java/javase/21/language/java-language-changes.html)
