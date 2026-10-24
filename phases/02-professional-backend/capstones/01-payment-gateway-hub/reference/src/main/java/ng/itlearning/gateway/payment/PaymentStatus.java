package ng.itlearning.gateway.payment;

public enum PaymentStatus {
    INITIATED, PENDING, SUCCEEDED, FAILED, ABANDONED, PARTIALLY_REFUNDED, REFUNDED;

    public boolean isFinal() {
        return this == FAILED || this == ABANDONED || this == REFUNDED;
    }
}
