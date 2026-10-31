package ng.itlearning.pay.assistant;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Accounts, transfers and rates for the tests: A1 and TRF-1 belong to customer C1; A2 and TRF-9 to someone else. */
final class StubServices {

    private static final Map<String, String> BODIES = Map.of(
            "/accounts/A1", "{\"accountId\":\"A1\",\"available\":50000.00}",
            "/accounts/A2", "{\"accountId\":\"A2\",\"available\":1000.00}",
            "/transfers/TRF-1", "{\"reference\":\"TRF-1\",\"fromAccount\":\"A1\",\"toAccount\":\"A2\",\"amount\":2500.00,\"state\":\"CLEARED\",\"reason\":null}",
            "/transfers/TRF-9", "{\"reference\":\"TRF-9\",\"fromAccount\":\"A2\",\"toAccount\":\"A1\",\"amount\":900.00,\"state\":\"COMPLETED\"}",
            "/rates/USD-NGN", "{\"pair\":\"USD-NGN\",\"bid\":1530.00,\"ask\":1540.00}");

    /** Every path requested, so a test can prove a service was or wasn't called. */
    final List<String> served = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    StubServices() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                served.add(path);
                String body = BODIES.get(path);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    exchange.sendResponseHeaders(200, body.getBytes().length);
                    exchange.getResponseBody().write(body.getBytes());
                }
                exchange.close();
            });
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void register(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + server.getAddress().getPort();
        registry.add("itlpay.accounts-url", () -> url);
        registry.add("itlpay.transfers-url", () -> url);
        registry.add("itlpay.rates-url", () -> url);
    }

    void stop() {
        server.stop(0);
    }
}
