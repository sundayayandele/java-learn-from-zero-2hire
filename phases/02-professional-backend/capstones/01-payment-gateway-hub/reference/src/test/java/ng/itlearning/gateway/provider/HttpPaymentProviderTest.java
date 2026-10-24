package ng.itlearning.gateway.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import ng.itlearning.gateway.config.GatewayProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPaymentProviderTest {

    static final WireMockServer wm = new WireMockServer(options().dynamicPort());
    HttpPaymentProvider provider;

    @BeforeAll
    static void start() {
        wm.start();
    }

    @AfterAll
    static void stop() {
        wm.stop();
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        provider = new HttpPaymentProvider(new GatewayProperties.Provider("provider-a", wm.baseUrl(), "sk_test_x", 1,
                Set.of("NGN")), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void initialiseSendsTheContractAndReadsTheAuthorisationUrl() {
        wm.stubFor(post("/transaction/initialize").willReturn(okJson("""
                {"status":true,"data":{"authorization_url":"https://checkout.test/abc","access_code":"abc","reference":"pay_1"}}""")));
        var result = provider.initialise(new PaymentProvider.InitRequest("pay_1", 50_000, "NGN", "a@b.ng", "https://cb"));
        assertThat(result.authorizationUrl()).isEqualTo("https://checkout.test/abc");
        wm.verify(postRequestedFor(urlEqualTo("/transaction/initialize"))
                .withHeader("Authorization", equalTo("Bearer sk_test_x"))
                .withRequestBody(equalToJson("""
                        {"reference":"pay_1","amount":50000,"currency":"NGN","email":"a@b.ng","callback_url":"https://cb"}""")));
    }

    @Test
    void verifyMapsTheProviderStatus() {
        wm.stubFor(get("/transaction/verify/pay_1").willReturn(okJson("""
                {"status":true,"data":{"reference":"pay_1","status":"success","amount":50000,"currency":"NGN","id":"9"}}""")));
        var result = provider.verify("pay_1");
        assertThat(result.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
        assertThat(result.amountMinor()).isEqualTo(50_000);
    }

    @Test
    void serverErrorsAndTimeoutsAreUnavailable() {
        wm.stubFor(get("/transaction/verify/pay_5xx").willReturn(aResponse().withStatus(503)));
        wm.stubFor(get("/transaction/verify/pay_slow").willReturn(okJson("{}").withFixedDelay(2500)));
        assertThatThrownBy(() -> provider.verify("pay_5xx")).isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> provider.verify("pay_slow")).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void clientErrorsAreRejectionsNotOutages() {
        wm.stubFor(post("/refund").willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> provider.refund("pay_1", 100)).isInstanceOf(ProviderRejectedException.class);
    }

    @Test
    void refundReturnsTheProviderRefund() {
        wm.stubFor(post("/refund").willReturn(okJson("{\"status\":true,\"data\":{\"id\":\"rf_1\",\"status\":\"pending\"}}")));
        assertThat(provider.refund("pay_1", 100)).isEqualTo(new PaymentProvider.RefundResult("rf_1", "pending"));
        assertThat(provider.supports("NGN")).isTrue();
        assertThat(provider.supports("KES")).isFalse();
    }

    @Test
    void malformedSuccessBodiesAreTreatedAsUnavailable() {
        wm.stubFor(post("/transaction/initialize").willReturn(okJson("{\"status\":false}")));
        assertThatThrownBy(() -> provider.initialise(new PaymentProvider.InitRequest("p", 100, "NGN", "a@b.ng", "x")))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
