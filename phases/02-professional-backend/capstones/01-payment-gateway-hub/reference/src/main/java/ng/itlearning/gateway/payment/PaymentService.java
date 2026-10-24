package ng.itlearning.gateway.payment;

import io.micrometer.core.instrument.MeterRegistry;
import ng.itlearning.gateway.config.GatewayProperties;
import ng.itlearning.gateway.provider.NoProviderAvailableException;
import ng.itlearning.gateway.provider.PaymentProvider;
import ng.itlearning.gateway.provider.ProviderOutcome;
import ng.itlearning.gateway.provider.ProviderRouter;
import ng.itlearning.gateway.provider.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Initialisation, verification and refunds. Provider calls happen outside database transactions;
 * every status change happens inside one, on a locked payment row, through {@link PaymentStateMachine}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository payments;
    private final IdempotencyRepository keys;
    private final ProviderRouter router;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final GatewayProperties props;
    private final Clock clock;
    private final MeterRegistry meters;

    public PaymentService(PaymentRepository payments, IdempotencyRepository keys, ProviderRouter router,
                          TransactionTemplate tx, JsonMapper json, GatewayProperties props, Clock clock, MeterRegistry meters) {
        this.payments = payments;
        this.keys = keys;
        this.router = router;
        this.tx = tx;
        this.json = json;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
    }

    public PaymentView create(String merchantId, String idempotencyKey, CreatePayment request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "idempotency-key-missing",
                    "Send a unique Idempotency-Key header (at most 100 characters) with every new payment.");
        }
        Long max = props.limits().get(request.currency());
        if (max != null && request.amount() > max) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "validation-failed",
                    "amount exceeds the maximum of " + max + " for " + request.currency() + ".");
        }
        String hash = sha256(json.writeValueAsBytes(request));
        Instant now = clock.instant();
        boolean claimed = Boolean.TRUE.equals(tx.execute(s ->
                keys.claim(merchantId, idempotencyKey, hash, now, now.plus(props.idempotencyTtl()))));
        if (!claimed) {
            return replay(merchantId, idempotencyKey, hash);
        }

        String reference = "pay_" + HexFormat.of().formatHex(randomBytes());
        long id = tx.execute(s -> {
            long newId = payments.insert(reference, merchantId, request.amount(), request.currency(), request.email(), now);
            payments.appendEvent(newId, null, PaymentStatus.INITIATED, "API", null, null, now);
            return newId;
        });

        ProviderRouter.Routed<PaymentProvider.InitResult> routed;
        try {
            routed = router.initialise(request.currency(), p -> p.initialise(new PaymentProvider.InitRequest(
                    reference, request.amount(), request.currency(), request.email(), props.callbackUrl())));
        } catch (NoProviderAvailableException e) {
            tx.executeWithoutResult(s -> {
                payments.updateStatus(id, PaymentStatus.FAILED, clock.instant());
                payments.appendEvent(id, PaymentStatus.INITIATED, PaymentStatus.FAILED, "NO_PROVIDER", null, null, clock.instant());
                keys.release(merchantId, idempotencyKey);
            });
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "no-provider-available", e.getMessage(), Duration.ofSeconds(30));
        }

        String provider = routed.provider().name();
        PaymentView view = tx.execute(s -> {
            Instant at = clock.instant();
            payments.markPending(id, provider, routed.result().authorizationUrl(), at);
            payments.appendEvent(id, PaymentStatus.INITIATED, PaymentStatus.PENDING, "PROVIDER_INITIALISED", null, provider, at);
            PaymentView v = view(payments.find(reference).orElseThrow());
            keys.saveResponse(merchantId, idempotencyKey, json.writeValueAsString(v));
            return v;
        });
        meters.counter("payments.initialised", "provider", provider, "currency", request.currency()).increment();
        return view;
    }

    private PaymentView replay(String merchantId, String key, String hash) {
        for (int attempt = 0; attempt < 50; attempt++) {
            IdempotencyRepository.Stored stored = keys.find(merchantId, key).orElse(null);
            if (stored == null) {
                break;                                   // released after a failure: let the client retry
            }
            if (!stored.requestHash().equals(hash)) {
                throw new GatewayException(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused",
                        "This Idempotency-Key was already used with a different request.");
            }
            if (stored.responseJson() != null) {
                return json.readValue(stored.responseJson(), PaymentView.class);
            }
            sleep(100);                                  // the first request is still talking to the provider
        }
        throw new GatewayException(HttpStatus.CONFLICT, "idempotency-request-in-progress",
                "A request with this Idempotency-Key is still being processed; retry shortly.", Duration.ofSeconds(2));
    }

    public PaymentView get(String merchantId, String reference) {
        return view(owned(merchantId, reference));
    }

    public List<Payment> page(String merchantId, PaymentStatus status, Instant from, Instant to, String cursor, int limit) {
        return payments.page(merchantId, status, from, to, cursor == null ? null : Cursor.decode(cursor), limit);
    }

    /** Asks the provider for the latest result (retried with backoff) and applies it. */
    public PaymentView verify(String merchantId, String reference) {
        Payment p = owned(merchantId, reference);
        if (p.provider() == null) {
            return view(p);
        }
        PaymentProvider.VerifyResult result;
        try {
            result = router.idempotentCall(p.provider(), provider -> provider.verify(reference));
        } catch (ProviderUnavailableException e) {
            throw new GatewayException(HttpStatus.BAD_GATEWAY, "provider-unavailable", "The payment provider is not responding.");
        }
        apply(reference, result.outcome(), "VERIFY", null, result.amountMinor(), result.currency());
        return view(payments.find(reference).orElseThrow());
    }

    /**
     * Applies a provider result under a row lock. Results that the state machine rejects (late or out-of-order)
     * are recorded as notes, not applied; a success for an abandoned payment is flagged for operations.
     */
    public void apply(String reference, ProviderOutcome outcome, String cause, String providerEventId,
                      Long amountMinor, String currency) {
        tx.executeWithoutResult(s -> {
            Payment p = payments.lock(reference).orElse(null);
            if (p == null) {
                log.warn("Provider result for unknown payment {}", reference);
                return;
            }
            Instant now = clock.instant();
            if (outcome == ProviderOutcome.SUCCESS && amountMinor != null
                    && (amountMinor != p.amountMinor() || !p.currency().equals(currency))) {
                payments.appendEvent(p.id(), p.status(), p.status(), cause, providerEventId, "AMOUNT_MISMATCH_FLAGGED", now);
                meters.counter("payments.flagged", "reason", "amount-mismatch").increment();
                return;
            }
            var next = PaymentStateMachine.next(p.status(), outcome);
            if (next.isEmpty()) {
                String note = p.status() == PaymentStatus.ABANDONED && outcome == ProviderOutcome.SUCCESS
                        ? "LATE_SUCCESS_FLAGGED" : "IGNORED_" + outcome;
                payments.appendEvent(p.id(), p.status(), p.status(), cause, providerEventId, note, now);
                if (note.equals("LATE_SUCCESS_FLAGGED")) {
                    meters.counter("payments.flagged", "reason", "late-success").increment();
                }
                return;
            }
            payments.updateStatus(p.id(), next.get(), now);
            payments.appendEvent(p.id(), p.status(), next.get(), cause, providerEventId, null, now);
            meters.counter("payments.completed", "provider", String.valueOf(p.provider()), "status", next.get().name()).increment();
        });
    }

    public PaymentView refund(String merchantId, String reference, long amountMinor) {
        owned(merchantId, reference);
        return tx.execute(s -> {
            Payment p = payments.lock(reference).orElseThrow(() -> GatewayException.notFound(reference));
            if (p.status() != PaymentStatus.SUCCEEDED && p.status() != PaymentStatus.PARTIALLY_REFUNDED) {
                throw GatewayException.invalidTransition(p.status(), PaymentStatus.REFUNDED);
            }
            long after = p.refundedMinor() + amountMinor;
            if (after > p.amountMinor()) {
                throw new GatewayException(HttpStatus.UNPROCESSABLE_CONTENT, "refund-exceeds-amount",
                        "Refunds would total " + after + " but the payment was " + p.amountMinor() + ".");
            }
            PaymentStatus next = PaymentStateMachine.afterRefund(p.status(), p.amountMinor(), after);
            // The row stays locked during this one provider call so concurrent refunds can't both pass the check.
            PaymentProvider.RefundResult r;
            try {
                r = router.singleCall(p.provider(), provider -> provider.refund(reference, amountMinor));
            } catch (ProviderUnavailableException e) {
                throw new GatewayException(HttpStatus.BAD_GATEWAY, "provider-unavailable", "The payment provider is not responding.");
            }
            Instant now = clock.instant();
            payments.insertRefund(p.id(), amountMinor, r.id(), r.status(), now);
            payments.addRefund(p.id(), after, next, now);
            payments.appendEvent(p.id(), p.status(), next, "REFUND", null, "refund " + amountMinor, now);
            return view(payments.find(reference).orElseThrow());
        });
    }

    /** Merchants only ever see their own payments; anyone else's reference looks like it doesn't exist. */
    private Payment owned(String merchantId, String reference) {
        return payments.find(reference)
                .filter(p -> merchantId == null || p.merchantId().equals(merchantId))
                .orElseThrow(() -> GatewayException.notFound(reference));
    }

    private PaymentView view(Payment p) {
        return PaymentView.of(p, payments.events(p.id()));
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return b;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
