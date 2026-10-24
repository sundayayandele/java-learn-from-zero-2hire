package ng.itlearning.practice.design;

/**
 * Week 23. Pattern: the equals/hashCode contract.
 * <p>
 * A key identifying an account by bank code and account number, used in HashMaps and HashSets. Two keys are
 * equal when both parts are equal, ignoring leading and trailing spaces. Implement {@code equals} and
 * {@code hashCode} by hand (not as a record) so that equal keys always have equal hash codes, and make the
 * class immutable. Interviewers often ask you to do exactly this on a whiteboard.
 */
public final class AccountKey {

    private final String bankCode;
    private final String accountNumber;

    public AccountKey(String bankCode, String accountNumber) {
        this.bankCode = bankCode.strip();
        this.accountNumber = accountNumber.strip();
    }

    public String bankCode() {
        return bankCode;
    }

    public String accountNumber() {
        return accountNumber;
    }

    @Override
    public boolean equals(Object o) {
        //>>
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountKey other)) {
            return false;
        }
        return bankCode.equals(other.bankCode) && accountNumber.equals(other.accountNumber);
        //<<
    }

    @Override
    public int hashCode() {
        //>>
        return 31 * bankCode.hashCode() + accountNumber.hashCode();
        //<<
    }

    @Override
    public String toString() {
        return bankCode + "/" + accountNumber;
    }
}
