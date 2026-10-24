package ng.itlearning.lab.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Map;

/** Calls the payment provider (a Paystack-like API; WireMock in the tests). */
@Component
public class ProviderClient {

    private final RestClient http;

    public ProviderClient(@Value("${provider.base-url}") String baseUrl, @Value("${provider.secret}") String secret) {
        this.http = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1))
                        .build())
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secret)
                .build();
    }

    public record InitResponse(boolean status, Map<String, Object> data) {
    }

    /** Starts a payment at the provider and returns its authorisation URL. */
    public String initialise(String reference, long amountKobo, String email) {
        RestClientException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                InitResponse res = http.post().uri("/transaction/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reference", reference, "amount", amountKobo, "email", email))
                        .retrieve()
                        .body(InitResponse.class);
                return String.valueOf(res.data().get("authorization_url"));
            } catch (RestClientException e) {
                last = e;                                  // try again, the provider is probably just busy
            }
        }
        throw new ProviderException("Provider failed: " + last.getMessage(), last);
    }

    public static class ProviderException extends RuntimeException {
        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
