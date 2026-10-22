package ng.itlearning.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ng.itlearning.ledger.journal.Direction;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account")
public class Account {

    public enum Kind { CUSTOMER, CASH, FEE_INCOME, SUSPENSE }

    public enum Status { ACTIVE, FROZEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String number;

    @Column(name = "holder_name", nullable = false, length = 200)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_side", nullable = false, length = 1)
    private Direction normalSide;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "daily_limit", precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    protected Account() { }

    public static Account customer(String number, String holderName, String currency, BigDecimal dailyLimit, Instant openedAt) {
        Account a = new Account();
        a.number = number;
        a.holderName = holderName;
        a.kind = Kind.CUSTOMER;
        a.normalSide = Direction.C;            // a customer deposit is a liability of the bank
        a.currency = currency;
        a.status = Status.ACTIVE;
        a.dailyLimit = dailyLimit;
        a.balance = BigDecimal.ZERO.setScale(2);
        a.openedAt = openedAt;
        return a;
    }

    /** Applies a posting to the stored balance, from this account's point of view. */
    public void apply(Direction direction, BigDecimal amount) {
        BigDecimal signed = direction == normalSide ? amount : amount.negate();
        BigDecimal next = balance.add(signed);
        if (kind == Kind.CUSTOMER && next.signum() < 0) {
            throw new InsufficientFundsException(number, amount, balance);
        }
        balance = next;
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw new AccountNotActiveException(number, status);
        }
    }

    public void freeze() { status = Status.FROZEN; }

    public void unfreeze() {
        if (status == Status.CLOSED) {
            throw new AccountNotActiveException(number, status);
        }
        status = Status.ACTIVE;
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public String getHolderName() { return holderName; }
    public Kind getKind() { return kind; }
    public Direction getNormalSide() { return normalSide; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public BigDecimal getBalance() { return balance; }
    public Instant getOpenedAt() { return openedAt; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Account other && number != null && number.equals(other.number));
    }

    @Override
    public int hashCode() { return number == null ? 0 : number.hashCode(); }

    @Override
    public String toString() { return "Account[" + AccountNumbers.mask(number) + "]"; }
}
