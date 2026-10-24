package ng.itlearning.lab.flaky;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A tiny fake SMS gateway for tests. */
final class FakeSmsGateway implements AutoCloseable {

    final List<String> received = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    FakeSmsGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 8089), 0);
        server.createContext("/sms", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
