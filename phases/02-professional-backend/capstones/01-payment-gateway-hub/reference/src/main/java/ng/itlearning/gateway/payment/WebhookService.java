package ng.itlearning.gateway.payment;

import io.micrometer.core.instrument.MeterRegistry;
import ng.itlearning.gateway.provider.PaymentProvider;
import ng.itlearning.gateway.provider.ProviderOutcome;
import ng.itlearning.gateway.provider.ProviderRouter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Signed webhooks (topic 5.4): verify the signature over the raw bytes first, then record the event id once,
 * then apply the result through the payment service's locked state machine.
 */
@Service
public class WebhookService {

    private final ProviderRouter router;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final MeterRegistry meters;

    public WebhookService(ProviderRouter router, PaymentRepository payments, PaymentService paymentService,
                          TransactionTemplate tx, JsonMapper json, Clock clock, MeterRegistry meters) {
        this.router = router;
        this.payments = payments;
        this.paymentService = paymentService;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
        this.meters = meters;
    }

    public void handle(String providerName, byte[] body, String signature) {
        PaymentProvider provider = router.provider(providerName).orElseThrow(() ->
                new GatewayException(HttpStatus.NOT_FOUND, "provider-not-found", "Unknown provider."));
        if (!provider.signatureValid(body, signature)) {
            meters.counter("webhooks.rejected", "reason", "signature").increment();
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "invalid-signature", "The webhook signature is not valid.");
        }
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException e) {
            meters.counter("webhooks.rejected", "reason", "malformed").increment();
            throw new GatewayException(HttpStatus.BAD_REQUEST, "malformed-webhook", "The webhook body is not valid JSON.");
        }
        String type = event.path("event").asString("");
        JsonNode data = event.path("data");
        String eventId = type + ":" + data.path("id").asString("");
        String reference = data.path("reference").asString("");
        if (reference.isEmpty() || data.path("id").isMissingNode()) {
            meters.counter("webhooks.rejected", "reason", "malformed").increment();
            throw new GatewayException(HttpStatus.BAD_REQUEST, "malformed-webhook", "The webhook has no id or reference.");
        }
        // The unique key makes duplicates (even concurrent ones) a no-op; we still answer 200 so the provider stops retrying.
        boolean first = Boolean.TRUE.equals(tx.execute(s -> payments.recordWebhookEvent(providerName, eventId, clock.instant())));
        if (!first) {
            meters.counter("webhooks.duplicates", "provider", providerName).increment();
            return;
        }
        ProviderOutcome outcome = switch (type) {
            case "charge.success" -> ProviderOutcome.SUCCESS;
            case "charge.failed" -> ProviderOutcome.FAILED;
            default -> null;                              // refund.processed and others: recorded, nothing to apply
        };
        if (outcome != null) {
            paymentService.apply(reference, outcome, "WEBHOOK", eventId,
                    data.path("amount").isNumber() ? data.path("amount").asLong() : null,
                    data.path("currency").asString(null));
        }
    }
}
