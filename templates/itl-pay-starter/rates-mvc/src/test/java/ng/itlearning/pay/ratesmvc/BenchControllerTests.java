package ng.itlearning.pay.ratesmvc;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BenchControllerTests.Containers.class)
class BenchControllerTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine").withInitScript("schema.sql");
        }
    }

    static final HttpServer fakeProvider = startFakeProvider();

    static HttpServer startFakeProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/provider", exchange -> {
                byte[] body = "{\"pair\":\"USD-NGN\",\"ask\":1540.0}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("provider.url", () -> "http://localhost:" + fakeProvider.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeProvider() {
        fakeProvider.stop(0);
    }

    @LocalServerPort int port;

    @Test
    void quotesTheHigherOfTheProviderAndOurOwnAsk() {
        String body = RestClient.create("http://localhost:" + port).get().uri("/bench/quote/USD-NGN")
                .retrieve().body(String.class);
        assertThat(body).contains("\"pair\":\"USD-NGN\"").contains("\"rate\":1540.0");   // provider 1540 > our 1535
    }
}
