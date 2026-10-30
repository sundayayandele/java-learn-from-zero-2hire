package ng.itlearning.pay.fraud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Screens every reserved transfer and publishes a decision. Failures go to retry topics
 * (non-blocking: other transfers keep flowing), then to a dead-letter topic (topic 11.4).
 */
@Component
class FraudScreening {

    @JsonIgnoreProperties(ignoreUnknown = true)              // tolerant reader: new fields don't break us
    record TransferEvent(String type, String reference, String from, BigDecimal amount) { }

    private static final Logger log = LoggerFactory.getLogger(FraudScreening.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final BigDecimal maxAmount;

    FraudScreening(KafkaTemplate<String, String> kafka, JsonMapper json, @Value("${fraud.max-amount}") BigDecimal maxAmount) {
        this.kafka = kafka;
        this.json = json;
        this.maxAmount = maxAmount;
    }

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000, multiplier = 2.0),
            retryTopicSuffix = "-fraud-retry", dltTopicSuffix = "-fraud-dlt",   // per consumer group, so other services' retries don't mix
            exclude = JacksonException.class)                                 // a message we can't read won't improve: straight to the DLT
    @KafkaListener(topics = "transfer-events", groupId = "fraud")
    void on(String message) throws Exception {
        var event = json.readValue(message, TransferEvent.class);
        if (!"TransferReserved".equals(event.type())) {
            return;
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("reference", event.reference());
        if (event.amount().compareTo(maxAmount) > 0) {
            decision.put("decision", "REJECTED");
            decision.put("reason", "over the single-transfer limit of " + maxAmount.toPlainString());
        } else {
            decision.put("decision", "CLEARED");
        }
        // The same event always gets the same decision, so a redelivery publishes a harmless duplicate.
        kafka.send("fraud-decisions", event.reference(), json.writeValueAsString(decision)).get(10, TimeUnit.SECONDS);
        log.info("{} {}", event.reference(), decision.get("decision"));
    }

    @DltHandler
    void dead(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("dead letter on {}: {}", topic, message);   // alert on this, then investigate and replay (topic 11.4)
    }
}
