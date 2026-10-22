package ng.itlearning.ledger.journal;

/** Debit or credit. */
public enum Direction {
    D, C;

    public Direction opposite() {
        return this == D ? C : D;
    }
}
