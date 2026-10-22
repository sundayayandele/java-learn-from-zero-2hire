package ng.itlearning.ledger.account;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String accountNumber, Account.Status status) {
        super("Account " + AccountNumbers.mask(accountNumber) + " is " + status);
    }
}
