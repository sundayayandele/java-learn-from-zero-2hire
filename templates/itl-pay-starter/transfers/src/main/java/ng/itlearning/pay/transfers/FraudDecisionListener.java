package ng.itlearning.pay.transfers;

import ng.itlearning.pay.transfers.Transfers.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The saga's next step. Transfers is the orchestrator: it owns the state and decides what happens
 * when fraud screening answers. CLEARED moves on; REJECTED runs the compensation (release the hold).
 */
@Component
class FraudDecisionListener {

    record FraudDecision(String reference, String decision, String reason) { }

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionListener.class);

    private final Transfers transfers;
    private final AccountsClient accounts;
    private final JsonMapper json;

    FraudDecisionListener(Transfers transfers, AccountsClient accounts, JsonMapper json) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.json = json;
    }

    @KafkaListener(topics = "fraud-decisions", groupId = "transfers")
    void on(String message) {
        var decision = json.readValue(message, FraudDecision.class);
        var transfer = transfers.find(decision.reference()).orElse(null);
        if (transfer == null || transfer.state() != State.RESERVED) {
            log.info("ignoring {} for {}: already handled or unknown", decision.decision(), decision.reference());
            return;                                          // a redelivery: idempotent by state
        }
        if ("CLEARED".equals(decision.decision())) {
            transfers.move(transfer, State.RESERVED, State.CLEARED, "TransferCleared", null);
        } else {
            accounts.release(transfer.fromAccount(), transfer.reference());   // compensate first; repeatable if we crash here
            transfers.move(transfer, State.RESERVED, State.FAILED, "TransferFailed", decision.reason());
        }
    }
}
