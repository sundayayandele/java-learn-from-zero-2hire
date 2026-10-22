package ng.itlearning.ledger.journal;

public class AlreadyReversedException extends RuntimeException {

    public AlreadyReversedException(String reference) {
        super("Transfer " + reference + " has already been reversed");
    }
}
