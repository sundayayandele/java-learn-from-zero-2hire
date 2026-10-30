package ng.itlearning.pay.fraud;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
class FraudScreeningTests {

    @Container
    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka:4.1.1");

    @Autowired KafkaTemplate<String, String> kafka;

    @Test
    void clearsSmallTransfersRejectsLargeOnesAndDeadLettersPoisonMessages() {
        kafka.send("transfer-events", "T-SMALL", """
                {"type":"TransferReserved","reference":"T-SMALL","from":"A1","amount":2500.00,"addedLater":"ignored"}""");
        kafka.send("transfer-events", "T-BIG", """
                {"type":"TransferReserved","reference":"T-BIG","from":"A1","amount":25000.00}""");
        kafka.send("transfer-events", "T-SMALL", """
                {"type":"TransferCleared","reference":"T-SMALL","from":"A1","amount":2500.00}""");
        kafka.send("transfer-events", "T-BAD", "this is not JSON");

        List<String> decisions = read("fraud-decisions", 2);
        assertThat(decisions).anySatisfy(d -> assertThat(d).contains("T-SMALL", "CLEARED"));
        assertThat(decisions).anySatisfy(d -> assertThat(d).contains("T-BIG", "REJECTED", "single-transfer limit"));
        assertThat(read("transfer-events-fraud-dlt", 1)).containsExactly("this is not JSON");
    }

    List<String> read(String topic, int atLeast) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<String> values = new CopyOnWriteArrayList<>();
            await().atMost(Duration.ofSeconds(30)).pollDelay(Duration.ZERO).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> values.add(r.value()));
                return values.size() >= atLeast;
            });
            return values;
        }
    }
}
