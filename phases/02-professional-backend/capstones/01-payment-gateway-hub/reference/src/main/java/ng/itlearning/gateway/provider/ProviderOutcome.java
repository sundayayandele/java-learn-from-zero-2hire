package ng.itlearning.gateway.provider;

/** A provider's view of a charge, normalised from its own status strings. */
public enum ProviderOutcome {
    SUCCESS, FAILED, ABANDONED, ONGOING;

    public static ProviderOutcome fromProviderStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "success" -> SUCCESS;
            case "failed", "reversed" -> FAILED;
            case "abandoned" -> ABANDONED;
            default -> ONGOING;            // "ongoing", "pending", "processing" and anything new
        };
    }
}
