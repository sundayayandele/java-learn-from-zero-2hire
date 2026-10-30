package ng.itlearning.pay.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class TransferNotifierTests {

    @Container
    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka:4.1.1");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired JdbcClient jdbc;

    @Test
    void aDuplicateEventSendsOneSmsAndAPoisonMessageIsDeadLettered() {
        String reserved = """
                {"eventId":"0b6f6b0e-5d1e-4a43-9d0f-3c1f2a7e9a10","type":"TransferReserved","reference":"TRF-7","from":"A1","amount":2500.00}""";
        kafka.send("transfer-events", "TRF-7", reserved);
        kafka.send("transfer-events", "TRF-7", reserved);                   // the outbox relay may publish twice
        kafka.send("transfer-events", "TRF-8", "{\"eventId\": broken");

        assertThat(read("transfer-events-notifications-dlt")).containsExactly("{\"eventId\": broken");
        await().atMost(Duration.ofSeconds(20)).until(() -> count("TRF-7") == 1);
        assertThat(count("TRF-7")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT message FROM notification WHERE reference = 'TRF-7'").query(String.class).single())
                .isEqualTo("Your transfer TRF-7 of NGN 2500.00 is being processed.");
    }

    long count(String reference) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE reference = ?").param(reference).query(Long.class).single();
    }

    List<String> read(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<String> values = new CopyOnWriteArrayList<>();
            await().atMost(Duration.ofSeconds(30)).pollDelay(Duration.ZERO).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> values.add(r.value()));
                return !values.isEmpty();
            });
            return values;
        }
    }
}
