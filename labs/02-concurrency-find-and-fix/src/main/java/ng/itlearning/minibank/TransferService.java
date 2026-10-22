package ng.itlearning.minibank;

import java.math.BigDecimal;

public class TransferService {

    public boolean transfer(Wallet from, Wallet to, BigDecimal amount) {
        synchronized (from) {
            synchronized (to) {
                if (!from.withdraw(amount)) {
                    return false;
                }
                to.deposit(amount);
                return true;
            }
        }
    }
}
