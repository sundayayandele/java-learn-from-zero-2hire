package ng.itlearning.pay.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Tells the customer what happened to their transfer, exactly once per event even though Kafka may deliver it twice. */
@Component
class TransferNotifier {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransferEvent(UUID eventId, String type, String reference, String from, BigDecimal amount, String reason) { }

    private static final Logger log = LoggerFactory.getLogger(TransferNotifier.class);

    private final JdbcClient jdbc;
    private final JsonMapper json;

    TransferNotifier(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = "transfer-events", groupId = "notifications")
    @Transactional
    public void on(String message) {
        var event = json.readValue(message, TransferEvent.class);
        String text = switch (event.type()) {
            case "TransferReserved" -> "Your transfer %s of NGN %s is being processed.";
            case "TransferCleared" -> "Your transfer %s of NGN %s has been sent.";
            case "TransferFailed", "TransferRejected" -> "Your transfer %s of NGN %s was not completed: " + event.reason() + ".";
            default -> null;
        };
        if (text == null) {
            return;
        }
        int fresh = jdbc.sql("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING")
                .param(event.eventId()).update();
        if (fresh == 0) {
            log.info("duplicate {} for {}: already notified", event.type(), event.reference());
            return;
        }
        String sms = text.formatted(event.reference(), event.amount().toPlainString());
        jdbc.sql("INSERT INTO notification (reference, account_id, message) VALUES (?, ?, ?)")
                .params(event.reference(), event.from(), sms).update();
        log.info("SMS to {}: {}", event.from(), sms);     // a real service calls the SMS provider here, with its own retries
    }
}
