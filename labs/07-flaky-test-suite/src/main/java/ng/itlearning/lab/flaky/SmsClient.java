package ng.itlearning.lab.flaky;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Sends an SMS through the gateway at {@code http://localhost:8089}. */
public final class SmsClient {

    private final HttpClient http = HttpClient.newHttpClient();

    public int send(String phone, String text) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8089/sms"))
                .POST(HttpRequest.BodyPublishers.ofString(phone + "|" + text))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
