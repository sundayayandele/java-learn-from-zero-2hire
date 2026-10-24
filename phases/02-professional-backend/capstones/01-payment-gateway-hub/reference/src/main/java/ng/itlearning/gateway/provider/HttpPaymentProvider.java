package ng.itlearning.gateway.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import ng.itlearning.gateway.config.GatewayProperties;
import ng.itlearning.gateway.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A provider that follows the brief's (Paystack-like) contract over HTTPS.
 * Timeouts are explicit; 5xx and I/O errors become {@link ProviderUnavailableException}, 4xx {@link ProviderRejectedException}.
 */
public class HttpPaymentProvider implements PaymentProvider {

    private final GatewayProperties.Provider config;
    private final RestClient http;

    public HttpPaymentProvider(GatewayProperties.Provider config, Duration connectTimeout, Duration readTimeout) {
        this.config = config;
        this.http = RestClient.builder()
                .baseUrl(config.baseUrl())
                // HTTP/1.1: providers' APIs don't need HTTP/2, and cleartext h2c upgrades confuse some servers.
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
                .defaultHeader("Authorization", "Bearer " + config.secret())
                // Pass our correlation id on, so the provider's support team can trace our calls (topic 2.6).
                .requestInterceptor((request, body, execution) -> {
                    String id = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (id != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, id);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new ProviderUnavailableException(config.name(), "HTTP " + res.getStatusCode().value());
                })
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new ProviderRejectedException(config.name(), res.getStatusCode().value());
                })
                .build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public int priority() {
        return config.priority();
    }

    @Override
    public boolean supports(String currency) {
        return config.currencies().contains(currency);
    }

    @Override
    public InitResult initialise(InitRequest r) {
        InitResponse body = call(() -> http.post().uri("/transaction/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reference", r.reference(), "amount", r.amountMinor(), "currency", r.currency(),
                        "email", r.email(), "callback_url", r.callbackUrl()))
                .retrieve().body(InitResponse.class));
        if (body == null || !body.status() || body.data() == null) {
            throw new ProviderUnavailableException(name(), "unexpected initialise response");
        }
        return new InitResult(body.data().authorizationUrl(), body.data().accessCode());
    }

    @Override
    public VerifyResult verify(String reference) {
        VerifyResponse body = call(() -> http.get().uri("/transaction/verify/{reference}", reference)
                .retrieve().body(VerifyResponse.class));
        if (body == null || !body.status() || body.data() == null) {
            throw new ProviderUnavailableException(name(), "unexpected verify response");
        }
        var d = body.data();
        return new VerifyResult(d.reference(), ProviderOutcome.fromProviderStatus(d.status()), d.amount(), d.currency());
    }

    @Override
    public RefundResult refund(String reference, long amountMinor) {
        RefundResponse body = call(() -> http.post().uri("/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transaction", reference, "amount", amountMinor))
                .retrieve().body(RefundResponse.class));
        if (body == null || !body.status() || body.data() == null) {
            throw new ProviderUnavailableException(name(), "unexpected refund response");
        }
        return new RefundResult(body.data().id(), body.data().status());
    }

    @Override
    public boolean signatureValid(byte[] body, String signature) {
        return HmacSignatures.valid(config.secret(), body, signature);
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {        // connect/read timeouts and I/O errors
            throw new ProviderUnavailableException(name(), e);
        }
    }

    record InitResponse(boolean status, InitData data) {
    }

    record InitData(@JsonProperty("authorization_url") String authorizationUrl,
                    @JsonProperty("access_code") String accessCode, String reference) {
    }

    record VerifyResponse(boolean status, VerifyData data) {
    }

    record VerifyData(String reference, String status, long amount, String currency, String id) {
    }

    record RefundResponse(boolean status, RefundData data) {
    }

    record RefundData(String id, String status) {
    }
}
