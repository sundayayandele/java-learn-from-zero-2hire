package ng.itlearning.lab.payments;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Five failing tests, one per planted bug. Make them all pass without weakening them. Each test name says
 * what the service must do; README.md explains the context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentResilienceTest {

    static final WireMockServer provider = new WireMockServer(options().dynamicPort());
    static final String SECRET = "sk_test_lab_secret";

    static {
        provider.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("provider.base-url", provider::baseUrl);
        r.add("provider.secret", () -> SECRET);
    }

    @AfterAll
    static void stop() {
        provider.stop();
    }

    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        provider.resetAll();
    }

    static final String BODY = "{\"amount\":250000,\"email\":\"ada@example.ng\"}";

    void stubOk() {
        provider.stubFor(WireMock.post("/transaction/initialize").willReturn(okJson(
                "{\"status\":true,\"data\":{\"authorization_url\":\"https://checkout.test/abc\"}}")));
    }

    @Test
    void bug1_aSlowProviderFailsFastInsteadOfHangingTheRequest() {
        provider.stubFor(WireMock.post("/transaction/initialize").willReturn(okJson("{}").withFixedDelay(10_000)));
        assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                mvc.perform(post("/payments").header("Idempotency-Key", "k-slow")
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andExpect(status().isBadGateway()));
    }

    @Test
    void bug2_initialiseIsNotRetriedBlindlyBecauseItIsNotIdempotent() throws Exception {
        provider.stubFor(WireMock.post("/transaction/initialize").willReturn(aResponse().withStatus(503)));
        mvc.perform(post("/payments").header("Idempotency-Key", "k-503")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway());
        provider.verify(1, postRequestedFor(urlEqualTo("/transaction/initialize")));
    }

    @Test
    void bug3_theSameIdempotencyKeyNeverCreatesASecondPayment() throws Exception {
        stubOk();
        String first = mvc.perform(post("/payments").header("Idempotency-Key", "k-same")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/payments").header("Idempotency-Key", "k-same")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        provider.verify(1, postRequestedFor(urlEqualTo("/transaction/initialize")));
    }

    @Test
    void bug4_webhookSignaturesAreCheckedOverTheExactBytesReceived() throws Exception {
        stubOk();
        String created = mvc.perform(post("/payments").header("Idempotency-Key", "k-hook")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reference = created.replaceAll(".*\"reference\":\"([^\"]+)\".*", "$1");
        // Providers format JSON their own way: spaces and key order differ from what Jackson would write.
        String raw = "{ \"data\": { \"reference\": \"" + reference + "\", \"amount\": 250000 },  \"event\": \"charge.success\" }";
        mvc.perform(post("/webhooks/provider").contentType(MediaType.APPLICATION_JSON).content(raw)
                        .header("x-provider-signature", hmac(raw)))
                .andExpect(status().isOk());
        mvc.perform(post("/webhooks/provider").contentType(MediaType.APPLICATION_JSON).content(raw)
                        .header("x-provider-signature", hmac(raw + " ")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bug5_providerInternalsNeverLeakToOurClients() throws Exception {
        provider.stubFor(WireMock.post("/transaction/initialize").willReturn(aResponse().withStatus(503)
                .withBody("{\"message\":\"db-primary 10.20.3.7:5432 connection refused; trace id 7f3e\"}")));
        String body = mvc.perform(post("/payments").header("Idempotency-Key", "k-leak")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("10.20.3.7").doesNotContain("5432").doesNotContain("trace id");
    }

    static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
