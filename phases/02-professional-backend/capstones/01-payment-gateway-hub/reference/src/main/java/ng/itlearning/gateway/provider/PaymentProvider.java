package ng.itlearning.gateway.provider;

/** One payment provider. The hub talks to every provider through this interface (topic 5.4). */
public interface PaymentProvider {

    String name();

    int priority();

    boolean supports(String currency);

    InitResult initialise(InitRequest request);

    VerifyResult verify(String reference);

    RefundResult refund(String reference, long amountMinor);

    /** Checks the webhook signature over the exact bytes received. */
    boolean signatureValid(byte[] body, String signature);

    record InitRequest(String reference, long amountMinor, String currency, String email, String callbackUrl) {
    }

    record InitResult(String authorizationUrl, String accessCode) {
    }

    record VerifyResult(String reference, ProviderOutcome outcome, long amountMinor, String currency) {
    }

    record RefundResult(String id, String status) {
    }
}
