package ng.itlearning.ledger.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountNumber) {
        super("Account " + AccountNumbers.mask(accountNumber) + " was not found");
    }
}
