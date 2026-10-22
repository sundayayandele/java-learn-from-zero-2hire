package ng.itlearning.ledger.account;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientFundsException(String accountNumber, BigDecimal requested, BigDecimal available) {
        super("Account " + AccountNumbers.mask(accountNumber) + " has insufficient funds");
        this.requested = requested;
        this.available = available;
    }

    public BigDecimal requested() { return requested; }
    public BigDecimal available() { return available; }
}
