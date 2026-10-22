## What you will be able to do

- Model a business domain with classes, objects, fields, constructors and methods.
- Apply encapsulation, abstraction, inheritance and polymorphism, and say when each helps.
- Choose between an interface, an abstract class and composition.
- Implement `equals`, `hashCode` and `toString` correctly.
- Recognise violations of the five SOLID principles in code review, and refactor them.

## Why this matters in a bank or fintech

Banking code lives for decades. The account, loan and payment classes you write will be changed by dozens of engineers after you, often under regulatory deadlines. Object-oriented design is how you keep that change safe: rules live next to the data they protect, new products plug in without rewriting old ones, and tests can replace a real payment gateway with a fake. SOLID questions are standard in Java interviews precisely because they predict whether your code will survive that.

## 1. Classes and objects

A **class** is a blueprint; an **object** is one instance of it with its own state.

```java
public class Account {
    private final String number;
    private final String ownerName;
    private BigDecimal balance;

    public Account(String number, String ownerName, BigDecimal openingBalance) {
        if (number == null || !number.matches("\\d{10}")) {
            throw new IllegalArgumentException("Account number must be 10 digits");
        }
        this.number = number;
        this.ownerName = ownerName;
        this.balance = openingBalance;
    }

    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(number, amount, balance);
        }
        balance = balance.subtract(amount);
    }

    public BigDecimal balance() { return balance; }
    public String number() { return number; }

    private static void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
    }
}
```

`this` refers to the current object. The constructor guarantees that no `Account` can exist with an invalid number: an object should be valid from the moment it is created.

## 2. The four pillars

### Encapsulation

Fields are `private`; the outside world changes state only through methods that enforce the rules. Nobody can write `account.balance = account.balance.subtract(x)` and bypass the overdraft check. Compare that with a class full of public getters *and setters* for every field (an "anaemic" model), where every caller must remember the rules and one of them eventually forgets.

Access modifiers, from most to least open: `public` (everyone), `protected` (subclasses and same package), *package-private* (no keyword: same package only), `private` (this class only). Start with `private` and open up only when you must.

### Abstraction

Expose *what* an object does, hide *how*. A caller of `PaymentGateway.charge(card, amount)` should not know whether it calls Paystack, Flutterwave or a test stub. Interfaces are Java's main abstraction tool:

```java
public interface PaymentGateway {
    PaymentResult charge(String cardToken, Money amount);
}
```

### Inheritance

A subclass `extends` a superclass, inheriting its fields and methods and adding or overriding behaviour:

```java
public class SavingsAccount extends Account {
    private final BigDecimal annualRate;

    public SavingsAccount(String number, String owner, BigDecimal opening, BigDecimal annualRate) {
        super(number, owner, opening);          // call the parent constructor first
        this.annualRate = annualRate;
    }

    public BigDecimal monthlyInterest() {
        return balance().multiply(annualRate)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_EVEN);
    }
}
```

Inheritance models an **is-a** relationship: a savings account *is an* account. Java allows only one superclass (single inheritance) but many interfaces. Every class ultimately extends `java.lang.Object`.

Use inheritance sparingly. Deep hierarchies (`Account → InterestBearingAccount → SavingsAccount → PremiumSavingsAccount`) become brittle: a change in a parent ripples unpredictably into every child (the "fragile base class" problem). Mark classes `final` unless you designed them to be extended.

### Polymorphism

"Many forms": a variable of a supertype can hold any subtype, and the method that runs is chosen at runtime from the actual object.

```java
List<PaymentGateway> gateways = List.of(new PaystackGateway(), new FlutterwaveGateway());
for (PaymentGateway g : gateways) {
    g.charge(token, amount);    // each object runs its own implementation
}
```

That is **runtime polymorphism** via method overriding (mark overrides with `@Override` so the compiler catches typos). Overloading (topic 1.2) is sometimes called compile-time polymorphism.

## 3. Interfaces, abstract classes and composition

| | Interface | Abstract class |
| --- | --- | --- |
| Purpose | A capability or contract | A partial implementation shared by close relatives |
| State (fields) | Constants only | Yes |
| Constructors | No | Yes |
| Methods | Abstract, plus `default` and `static` methods | Abstract and concrete |
| A class can have | Many | One |

```java
public abstract class Loan {
    protected final Money principal;
    protected Loan(Money principal) { this.principal = principal; }

    public abstract BigDecimal annualRate();        // each product decides

    public Money totalInterest(int months) {        // shared algorithm (template method)
        return principal.times(annualRate()).times(months).dividedBy(12);
    }
}
```

**Composition** means "has-a": an object holds other objects and delegates to them.

```java
public class TransferService {
    private final AccountRepository accounts;
    private final FraudCheck fraudCheck;
    private final Notifier notifier;

    public TransferService(AccountRepository accounts, FraudCheck fraudCheck, Notifier notifier) {
        this.accounts = accounts;
        this.fraudCheck = fraudCheck;
        this.notifier = notifier;
    }
}
```

**Favour composition over inheritance.** You can swap a `FraudCheck` at runtime or in a test; you cannot swap a superclass. Spring (Module 4) is built entirely on this idea: it constructs objects like `TransferService` and passes in their collaborators.

## 4. `equals`, `hashCode` and `toString`

`Object` provides defaults: `equals` compares references and `toString` prints something like `Account@1b6d3586`. For value-like objects, override them, and **always override `hashCode` together with `equals`**, or `HashMap` and `HashSet` break (topic 1.4).

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Account other)) return false;   // pattern matching instanceof (Java 16+)
    return number.equals(other.number);
}

@Override
public int hashCode() { return number.hashCode(); }

@Override
public String toString() { return "Account[" + number + "]"; }   // never print PINs or full card numbers
```

The contract: equal objects must have equal hash codes; `equals` must be reflexive, symmetric and transitive. Records (topic 1.6) generate all three for you.

## 5. SOLID

Five principles, popularised by Robert C. Martin, for code that is easy to change.

### S: Single Responsibility Principle

*A class should have one reason to change.*

```java
// Before: three reasons to change (business rules, SQL, email format)
class StatementService {
    List<Transaction> load(String acc) { /* JDBC */ }
    BigDecimal closingBalance(List<Transaction> txs) { /* rules */ }
    void emailPdf(String acc) { /* SMTP + PDF */ }
}
```

After: `TransactionRepository` (data access), `StatementCalculator` (rules) and `StatementMailer` (delivery). Each can be tested and changed alone. "Reason to change" usually maps to a stakeholder: the finance team, the DBA, the communications team.

### O: Open/Closed Principle

*Open for extension, closed for modification.* Add behaviour by adding code, not by editing working code.

```java
// Before: every new channel edits this method (and risks breaking the others)
BigDecimal fee(String channel, BigDecimal amount) {
    if (channel.equals("POS")) ... else if (channel.equals("USSD")) ... else if ...
}

// After: a new channel is a new class
interface FeeRule { boolean appliesTo(Transaction tx); Money fee(Transaction tx); }
class PosFeeRule implements FeeRule { ... }
class UssdFeeRule implements FeeRule { ... }

class FeeEngine {
    private final List<FeeRule> rules;
    Money feeFor(Transaction tx) {
        return rules.stream().filter(r -> r.appliesTo(tx)).findFirst()
                .map(r -> r.fee(tx)).orElse(Money.zero(tx.currency()));
    }
}
```

### L: Liskov Substitution Principle

*A subtype must be usable anywhere its supertype is expected, without surprises.* If `Account.withdraw` promises "succeeds when the balance covers the amount", a `FixedDepositAccount` that throws `UnsupportedOperationException` from `withdraw` breaks every caller that trusted the contract. The fix is a better model: fixed deposits are not withdrawable accounts, so they should not extend that type. Typical LSP smells: overrides that throw "not supported", that strengthen preconditions (accept fewer inputs) or weaken postconditions (promise less).

### I: Interface Segregation Principle

*Clients should not depend on methods they do not use.* A 30-method `BankingService` interface forces every implementer and every mock to deal with all 30. Split it by client: `BalanceQuery`, `TransferCommand`, `StatementExport`. A mobile-app adapter that only reads balances depends only on `BalanceQuery`.

### D: Dependency Inversion Principle

*Depend on abstractions, not concretions.* High-level policy (`TransferService`) should not `new` up low-level details (`PostgresAccountRepository`, `SmtpNotifier`). It should depend on interfaces, and receive implementations from outside, usually through the constructor (**dependency injection**):

```java
// Before: impossible to test without a real database and mail server
class TransferService {
    private final PostgresAccountRepository repo = new PostgresAccountRepository();
}

// After: tests pass in fakes; production passes real ones (Spring does it for you in Module 4)
class TransferService {
    private final AccountRepository repo;
    TransferService(AccountRepository repo) { this.repo = repo; }
}
```

SOLID is a set of heuristics, not laws. Applied dogmatically to a 50-line script it produces 20 tiny classes. Apply it where change is likely: business rules, integrations and anything regulators touch.

## Worked example: a transfer domain

```java
public interface AccountRepository {
    Account find(String number);
    void save(Account account);
}

public final class TransferService {
    private final AccountRepository accounts;
    private final List<TransferRule> rules;     // OCP: limits, sanctions, fraud, each its own class

    public TransferService(AccountRepository accounts, List<TransferRule> rules) {
        this.accounts = accounts;
        this.rules = rules;
    }

    public void transfer(String from, String to, BigDecimal amount) {
        Account source = accounts.find(from);
        Account target = accounts.find(to);
        rules.forEach(r -> r.check(source, target, amount));   // each rule throws if violated
        source.withdraw(amount);                                 // encapsulated invariants
        target.deposit(amount);
        accounts.save(source);
        accounts.save(target);
    }
}
```

In a test you pass an in-memory `AccountRepository` backed by a `HashMap` and an empty rule list, with no database needed. In Module 3 you will make the two saves atomic with a database transaction.

## Common mistakes

- Public fields, or a setter for every field, so the rules live everywhere except in the object.
- Inheritance for code reuse between unrelated things (`Customer extends DatabaseConnection`).
- Overriding `equals` without `hashCode`.
- `toString` that leaks PII or secrets into logs.
- "God classes" (`BankManager`, `Utils`) that everything depends on.
- Over-engineering: an interface with exactly one implementation and no test double "just in case".

## Practice

1. Model `Customer`, `Account`, `SavingsAccount` and `CurrentAccount` (with an overdraft limit). Put the overdraft rule inside `CurrentAccount.withdraw`, not in the caller.
2. Take the `if/else` fee method above and refactor it to the `FeeRule` design. Add a new `AgentBankingFeeRule` without editing `FeeEngine`.
3. Find the LSP violation: `class ReadOnlyAccount extends Account { @Override void deposit(...) { throw new UnsupportedOperationException(); } }`. Propose a better hierarchy.
4. Write `TransferService` with an in-memory repository and three tests: a successful transfer, insufficient funds, and a blocked account rule.

## Key terms

Class, object, constructor, encapsulation, abstraction, inheritance, polymorphism, `@Override`, interface, abstract class, composition, `equals`/`hashCode` contract, SOLID, dependency injection.

## Further reading

- [The Java Tutorials: Object-Oriented Programming Concepts](https://docs.oracle.com/javase/tutorial/java/concepts/index.html)
- [The Java Tutorials: Interfaces and Inheritance](https://docs.oracle.com/javase/tutorial/java/IandI/index.html)
- [`Object.equals` and `hashCode` contract (Java 21 API)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)
- Joshua Bloch, *Effective Java* (3rd edition), items 10–11 and 18 (book)
