package ng.itlearning.minibank;

import java.math.BigDecimal;

/** An in-memory wallet. Shared by many request threads. */
public class Wallet {

    private final String id;
    private BigDecimal balance;

    public Wallet(String id, BigDecimal openingBalance) {
        this.id = id;
        this.balance = openingBalance;
    }

    public String id() { return id; }

    public BigDecimal balance() { return balance; }

    public boolean withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) >= 0) {
            simulateWork();
            balance = balance.subtract(amount);
            return true;
        }
        return false;
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    static void simulateWork() {
        Thread.onSpinWait();
    }
}
