package ng.itlearning.pay.transfers;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka, oldest first, and marks them published.
 * At least once: a crash after the send and before the update publishes the row again,
 * so every consumer must be idempotent (topic 11.3).
 */
@Component
class OutboxRelay {

    record Row(long id, String topic, String msgKey, String payload, String traceparent) { }

    static final int BATCH = 100;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Tracer tracer;
    private final Propagator propagator;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx, MeterRegistry meters,
                ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
        // How long the oldest unpublished event has waited: 0 when the relay keeps up (topic 13.2)
        Gauge.builder("itlpay.outbox.oldest.age", this, OutboxRelay::oldestUnpublishedSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest event not yet published to Kafka")
                .register(meters);
    }

    /** Polls every outbox.poll-ms, and keeps going without a pause while there's a backlog. */
    @Scheduled(fixedDelayString = "${outbox.poll-ms}")
    public void publish() {
        Integer sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent != null && sent == BATCH);
    }

    /** One transaction per batch: lock up to 100 rows, send them all, wait once for every ack, mark them published. */
    private int publishBatch() {
        var rows = jdbc.sql("""
                SELECT id, topic, msg_key, payload, traceparent FROM outbox
                WHERE published_at IS NULL ORDER BY id LIMIT ?
                FOR UPDATE SKIP LOCKED""")            // several instances can run the relay without sending a row twice at once
                .param(BATCH)
                .query(Row.class).list();
        if (rows.isEmpty()) {
            return 0;
        }
        List<CompletableFuture<?>> acks = new ArrayList<>(rows.size());
        for (Row row : rows) {
            Span span = continueTrace(row).name("outbox publish").tag("transfer.reference", row.msgKey()).start();
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {   // the Kafka send becomes part of the original request's trace
                acks.add(kafka.send(row.topic(), row.msgKey(), row.payload()));   // pipelined: the idempotent producer keeps per-key order
            } finally {
                span.end();
            }
        }
        try {
            CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);   // every broker ack
        } catch (Exception e) {
            throw new IllegalStateException("Kafka didn't acknowledge the batch; it will be sent again", e);   // rolls back: rows stay unpublished
        }
        jdbc.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)")
                .param("ids", rows.stream().map(Row::id).toList())
                .update();
        return rows.size();
    }

    private Span.Builder continueTrace(Row row) {
        if (row.traceparent() == null) {
            return tracer.spanBuilder();
        }
        return propagator.extract(Map.of("traceparent", row.traceparent()), Map::get);
    }

    double oldestUnpublishedSeconds() {
        return jdbc.sql("SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0) FROM outbox WHERE published_at IS NULL")
                .query(Double.class).single();
    }
}
