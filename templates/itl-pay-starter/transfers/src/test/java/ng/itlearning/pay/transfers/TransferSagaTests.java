package ng.itlearning.pay.transfers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;

/** The transfer saga end to end, with real PostgreSQL and Kafka and a fake accounts service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.export.otlp.enabled=false")   // trace, but send spans nowhere
@AutoConfigureTracing
@Import(TestcontainersConfiguration.class)
class TransferSagaTests {

    static final List<String> accountCalls = new CopyOnWriteArrayList<>();
    static final HttpServer fakeAccounts = startFakeAccounts();

    /** Answers 201 to a hold up to ₦10,000, 422 above it, and 200 to a release; records every call. */
    static HttpServer startFakeAccounts() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/accounts", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String reference = body.replaceAll(".*\"reference\":\"([^\"]+)\".*", "$1");
                accountCalls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + (body.isEmpty() ? "" : " " + reference));
                boolean tooBig = body.contains("\"amount\":99999");
                int status = exchange.getRequestMethod().equals("DELETE") ? 200 : tooBig ? 422 : 201;
                byte[] reply = (tooBig
                        ? "{\"title\":\"Unprocessable Content\",\"status\":422,\"detail\":\"Insufficient funds\"}"
                        : "{}").getBytes();
                exchange.getResponseHeaders().add("Content-Type", tooBig ? "application/problem+json" : "application/json");
                exchange.sendResponseHeaders(status, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void accountsUrl(DynamicPropertyRegistry registry) {
        registry.add("accounts.url", () -> "http://localhost:" + fakeAccounts.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAccounts() {
        fakeAccounts.stop(0);
    }

    @LocalServerPort int port;
    @Autowired Transfers transfers;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaContainer kafkaContainer;
    @Autowired StringRedisTemplate redis;

    RestClient http() {
        return RestClient.create("http://localhost:" + port);
    }

    void requestTransfer(String reference, String amount) {
        http().post().uri("/transfers").header("Content-Type", "application/json")
                .body("{\"reference\":\"%s\",\"from\":\"A1\",\"to\":\"A2\",\"amount\":%s}".formatted(reference, amount))
                .retrieve().toBodilessEntity();
    }

    Transfers.State stateOf(String reference) {
        return transfers.find(reference).orElseThrow().state();
    }

    @Test
    void aRepeatedRequestReservesOnceAndTheEventLeavesThroughTheOutbox() {
        requestTransfer("TRF-OUTBOX", "2500.00");
        requestTransfer("TRF-OUTBOX", "2500.00");

        assertThat(stateOf("TRF-OUTBOX")).isEqualTo(Transfers.State.RESERVED);
        assertThat(accountCalls).containsOnlyOnce("POST /accounts/A1/holds TRF-OUTBOX");
        assertThat(eventsFor("TRF-OUTBOX")).containsExactly("TransferReserved");
    }

    @Test
    void aClearedTransferCompletes() {
        requestTransfer("TRF-CLEAR", "1000.00");
        kafka.send("fraud-decisions", "TRF-CLEAR", "{\"reference\":\"TRF-CLEAR\",\"decision\":\"CLEARED\"}");

        await().atMost(Duration.ofSeconds(20)).until(() -> stateOf("TRF-CLEAR") == Transfers.State.CLEARED);
        assertThat(accountCalls).doesNotContain("DELETE /accounts/A1/holds/TRF-CLEAR");
    }

    @Test
    void aFraudRejectionReleasesTheHoldOnceEvenIfTheDecisionArrivesTwice() {
        requestTransfer("TRF-FRAUD", "8000.00");
        String rejected = "{\"reference\":\"TRF-FRAUD\",\"decision\":\"REJECTED\",\"reason\":\"over the single-transfer limit\"}";
        kafka.send("fraud-decisions", "TRF-FRAUD", rejected);
        kafka.send("fraud-decisions", "TRF-FRAUD", rejected);

        await().atMost(Duration.ofSeconds(20)).until(() -> stateOf("TRF-FRAUD") == Transfers.State.FAILED);
        await().atMost(Duration.ofSeconds(10)).until(() -> eventsFor("TRF-FRAUD").size() == 2);
        assertThat(accountCalls).containsOnlyOnce("DELETE /accounts/A1/holds/TRF-FRAUD");
        assertThat(eventsFor("TRF-FRAUD")).containsExactly("TransferReserved", "TransferFailed");
    }

    @Test
    void theTraceContinuesFromTheHttpRequestThroughTheOutboxToKafka() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        http().post().uri("/transfers").header("Content-Type", "application/json")
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")      // as if the gateway had started the trace
                .body("{\"reference\":\"TRF-TRACE\",\"from\":\"A1\",\"to\":\"A2\",\"amount\":100.00}")
                .retrieve().toBodilessEntity();

        assertThat(headerFor("TRF-TRACE", "traceparent")).contains(traceId);   // same trace id on the Kafka record
    }

    @Test
    void theStatusIsCachedAndEvictedWhenTheTransferMoves() {
        requestTransfer("TRF-CACHE", "500.00");
        assertThat(http().get().uri("/transfers/TRF-CACHE").retrieve().body(String.class)).contains("RESERVED");
        assertThat(redis.hasKey("itl-pay:transfers::TRF-CACHE")).isTrue();                     // cache-aside filled it

        kafka.send("fraud-decisions", "TRF-CACHE", "{\"reference\":\"TRF-CACHE\",\"decision\":\"CLEARED\"}");
        await().atMost(Duration.ofSeconds(20)).until(() -> stateOf("TRF-CACHE") == Transfers.State.CLEARED);
        await().atMost(Duration.ofSeconds(5)).until(() -> !redis.hasKey("itl-pay:transfers::TRF-CACHE"));   // evicted after commit
        assertThat(http().get().uri("/transfers/TRF-CACHE").retrieve().body(String.class)).contains("CLEARED");
    }

    @Test
    void aRejectedReservationIsRecordedAndAnswered422() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> requestTransfer("TRF-NOFUNDS", "99999.00")))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
        assertThat(stateOf("TRF-NOFUNDS")).isEqualTo(Transfers.State.REJECTED);
    }

    /** The value of one header on the first transfer-events record for a reference. */
    String headerFor(String reference, String header) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Outbox.TOPIC));
            List<String> values = new CopyOnWriteArrayList<>();
            await().atMost(Duration.ofSeconds(20)).pollDelay(Duration.ZERO).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().equals(reference) && r.headers().lastHeader(header) != null) {
                        values.add(new String(r.headers().lastHeader(header).value()));
                    }
                });
                return !values.isEmpty();
            });
            return values.getFirst();
        }
    }

    /** Reads every event for one reference from transfer-events, in order. */
    List<String> eventsFor(String reference) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Outbox.TOPIC));
            List<String> types = new CopyOnWriteArrayList<>();
            await().atMost(Duration.ofSeconds(20)).pollDelay(Duration.ZERO).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().equals(reference)) {
                        types.add(r.value().replaceAll(".*\"type\":\"(\\w+)\".*", "$1"));
                    }
                });
                return !types.isEmpty();
            });
            consumer.poll(Duration.ofSeconds(1)).forEach(r -> {
                if (r.key().equals(reference)) {
                    types.add(r.value().replaceAll(".*\"type\":\"(\\w+)\".*", "$1"));
                }
            });
            return types;
        }
    }
}
