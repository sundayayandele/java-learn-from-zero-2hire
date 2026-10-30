package ng.itlearning.pay.transfers;

import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka, oldest first, and marks them published.
 * At least once: a crash after the send and before the update publishes the row again,
 * so every consumer must be idempotent (topic 11.3).
 */
@Component
class OutboxRelay {

    record Row(long id, String topic, String msgKey, String payload) { }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms}")
    @Transactional
    public void publish() throws Exception {
        var rows = jdbc.sql("""
                SELECT id, topic, msg_key, payload FROM outbox
                WHERE published_at IS NULL ORDER BY id LIMIT 100
                FOR UPDATE SKIP LOCKED""")            // several instances can run the relay without sending a row twice at once
                .query(Row.class).list();
        for (Row row : rows) {
            kafka.send(row.topic(), row.msgKey(), row.payload()).get(10, TimeUnit.SECONDS);   // wait for the broker's ack
            jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = ?").param(row.id()).update();
        }
    }
}
