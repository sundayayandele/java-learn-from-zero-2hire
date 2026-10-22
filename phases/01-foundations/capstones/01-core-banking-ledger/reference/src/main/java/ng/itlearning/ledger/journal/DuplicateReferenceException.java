package ng.itlearning.ledger.journal;

/** The reference was already used for a different request. */
public class DuplicateReferenceException extends RuntimeException {

    public DuplicateReferenceException(String reference) {
        super("Reference " + reference + " was already used for a different request");
    }
}
