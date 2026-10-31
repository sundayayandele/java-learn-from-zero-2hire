package ng.itlearning.pay.transfers;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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
    private final Tracer tracer;
    private final Propagator propagator;

    Outbox(JdbcClient jdbc, JsonMapper json, ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.jdbc = jdbc;
        this.json = json;
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
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
        jdbc.sql("INSERT INTO outbox (topic, msg_key, payload, traceparent) VALUES (?, ?, ?, ?)")
                .params(TOPIC, t.reference(), json.writeValueAsString(event), currentTraceparent())   // key = reference: one partition, in order
                .update();
    }

    /** The W3C traceparent of the current span, e.g. 00-4bf92f35...-00f067aa0ba902b7-01, or null outside a trace. */
    private String currentTraceparent() {
        var context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        var carrier = new HashMap<String, String>();
        propagator.inject(context, carrier, HashMap::put);
        return carrier.get("traceparent");
    }
}
