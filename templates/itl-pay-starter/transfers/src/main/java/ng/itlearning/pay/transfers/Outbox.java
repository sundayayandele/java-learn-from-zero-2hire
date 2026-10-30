package ng.itlearning.pay.transfers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes events into the outbox table, inside the caller's transaction. OutboxRelay publishes them. */
@Component
class Outbox {

    static final String TOPIC = "transfer-events";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Outbox(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)   // no transaction, no event: fail loudly
    void append(String type, Transfers.Transfer t, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("reference", t.reference());
        event.put("from", t.fromAccount());
        event.put("to", t.toAccount());
        event.put("amount", t.amount());
        event.put("reason", reason);
        event.put("occurredAt", Instant.now().toString());
        jdbc.sql("INSERT INTO outbox (topic, msg_key, payload) VALUES (?, ?, ?)")
                .params(TOPIC, t.reference(), json.writeValueAsString(event))   // key = reference: one partition, in order
                .update();
    }
}
