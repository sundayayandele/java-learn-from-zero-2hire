package ng.itlearning.ledger.journal;

public class JournalNotFoundException extends RuntimeException {

    public JournalNotFoundException(String reference) {
        super("No transfer with reference " + reference);
    }
}
