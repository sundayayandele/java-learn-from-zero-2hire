package ng.itlearning.gateway.payment;

import ng.itlearning.gateway.config.GatewayProperties;
import ng.itlearning.gateway.provider.ProviderOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Catches what webhooks missed: verifies payments pending longer than {@code pending-verify-after}
 * and abandons those older than {@code abandon-after}. Safe to run on several instances (row locks, idempotent).
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final PaymentRepository payments;
    private final PaymentService service;
    private final GatewayProperties props;
    private final Clock clock;

    public ReconciliationJob(PaymentRepository payments, PaymentService service, GatewayProperties props, Clock clock) {
        this.payments = payments;
        this.service = service;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gateway.reconcile-every}", initialDelayString = "${gateway.reconcile-every}")
    public int run() {
        Instant now = clock.instant();
        int handled = 0;
        for (String reference : payments.pendingSince(now.minus(props.pendingVerifyAfter()), 500)) {
            Payment p = payments.find(reference).orElseThrow();
            try {
                if (p.createdAt().isBefore(now.minus(props.abandonAfter()))) {
                    service.apply(reference, ProviderOutcome.ABANDONED, "RECONCILIATION", null, null, null);
                } else {
                    service.verify(null, reference);
                }
                handled++;
            } catch (RuntimeException e) {
                log.warn("Reconciliation of {} failed; will retry next run: {}", reference, e.getMessage());
            }
        }
        return handled;
    }
}
