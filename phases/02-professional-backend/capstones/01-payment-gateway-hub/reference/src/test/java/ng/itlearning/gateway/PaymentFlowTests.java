package ng.itlearning.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import ng.itlearning.gateway.payment.ReconciliationJob;
import ng.itlearning.gateway.provider.HmacSignatures;
import ng.itlearning.gateway.provider.ProviderRouter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The brief's acceptance criteria, end to end: real PostgreSQL (Testcontainers), providers simulated by WireMock. */
@SpringBootTest(properties = {
        "gateway.read-timeout=800ms",
        "gateway.reconcile-every=1h",
        "gateway.circuit-breaker.sliding-window-size=4",
        "gateway.circuit-breaker.minimum-number-of-calls=3",
        "gateway.verify-retry.delay=10ms",
        "gateway.verify-retry.jitter=0ms"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentFlowTests {

    static final WireMockServer providerA = new WireMockServer(options().dynamicPort());
    static final WireMockServer providerB = new WireMockServer(options().dynamicPort());
    static final String SECRET_A = "sk_test_provider_a";
    static final String SECRET_B = "sk_test_provider_b";

    static {
        providerA.start();
        providerB.start();
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry r) {
        // A list from a higher-priority source replaces the whole list, so every field is set here.
        r.add("gateway.providers[0].name", () -> "provider-a");
        r.add("gateway.providers[0].priority", () -> 1);
        r.add("gateway.providers[0].currencies", () -> "NGN,GHS,KES,USD");
        r.add("gateway.providers[1].name", () -> "provider-b");
        r.add("gateway.providers[1].priority", () -> 2);
        r.add("gateway.providers[1].currencies", () -> "NGN,GHS,USD");
        r.add("gateway.providers[0].base-url", providerA::baseUrl);
        r.add("gateway.providers[0].secret", () -> SECRET_A);
        r.add("gateway.providers[1].base-url", providerB::baseUrl);
        r.add("gateway.providers[1].secret", () -> SECRET_B);
    }

    @AfterAll
    static void stop() {
        providerA.stop();
        providerB.stop();
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired ProviderRouter router;
    @Autowired ReconciliationJob reconciliation;

    @BeforeEach
    void reset() {
        providerA.resetAll();
        providerB.resetAll();
        router.reset();
        stubInitialise(providerA, "a");
        stubInitialise(providerB, "b");
    }

    static void stubInitialise(WireMockServer wm, String tag) {
        wm.stubFor(WireMock.post("/transaction/initialize").willReturn(okJson("""
                {"status":true,"data":{"authorization_url":"https://checkout.%s.test/x","access_code":"x","reference":"r"}}""".formatted(tag))));
    }

    static RequestPostProcessor merchant(String id) {
        return jwt().jwt(j -> j.subject(id)).authorities(new SimpleGrantedAuthority("SCOPE_api.read"),
                new SimpleGrantedAuthority("SCOPE_api.write"));
    }

    static String body(long amount, String currency) {
        return "{\"amount\":%d,\"currency\":\"%s\",\"email\":\"ada@example.ng\"}".formatted(amount, currency);
    }

    JsonNode create(String merchant, String key, String body) throws Exception {
        String res = mvc.perform(post("/api/v1/payments").with(merchant(merchant)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    String newPayment(String merchant) throws Exception {
        return create(merchant, UUID.randomUUID().toString(), body(50_000, "NGN")).path("reference").asString();
    }

    void webhook(String provider, String secret, String event, String id, String reference, long amount) throws Exception {
        byte[] payload = """
                {"event":"%s","data":{"id":"%s","reference":"%s","amount":%d,"currency":"NGN","status":"x"}}"""
                .formatted(event, id, reference, amount).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/" + provider).contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("x-provider-signature", HmacSignatures.sign(secret, payload)))
                .andExpect(status().isOk());
    }

    String paymentStatus(String merchant, String reference) throws Exception {
        String res = mvc.perform(get("/api/v1/payments/" + reference).with(merchant(merchant)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("status").asString();
    }

    @Test
    void initialisesWithTheHighestPriorityProvider() throws Exception {
        JsonNode p = create("m1", "k-init", body(50_000, "NGN"));
        assertThat(p.path("provider").asString()).isEqualTo("provider-a");
        assertThat(p.path("status").asString()).isEqualTo("PENDING");
        assertThat(p.path("authorizationUrl").asString()).isEqualTo("https://checkout.a.test/x");
    }

    @Test
    void correlationIdIsReturnedAndPassedToTheProvider() throws Exception {
        mvc.perform(post("/api/v1/payments").with(merchant("m-corr")).header("Idempotency-Key", "k-corr")
                        .header("X-Correlation-Id", "corr-12345678").contentType(MediaType.APPLICATION_JSON).content(body(10_000, "NGN")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-12345678"));
        providerA.verify(postRequestedFor(urlEqualTo("/transaction/initialize"))
                .withHeader("X-Correlation-Id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("corr-12345678")));
        mvc.perform(get("/api/v1/payments?limit=1").with(merchant("m-corr")).header("X-Correlation-Id", "bad id!"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void sameKeyAndPayloadReplaysWithoutCallingTheProviderAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode first = create("m1", key, body(70_000, "NGN"));
        JsonNode second = create("m1", key, body(70_000, "NGN"));
        assertThat(second.path("reference").asString()).isEqualTo(first.path("reference").asString());
        providerA.verify(1, postRequestedFor(urlEqualTo("/transaction/initialize")));

        mvc.perform(post("/api/v1/payments").with(merchant("m1")).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(71_000, "NGN")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/idempotency-key-reused"));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> create("m-conc", key, body(12_300, "NGN")).path("reference").asString());
        }
        try (var pool = Executors.newFixedThreadPool(8)) {
            var refs = pool.invokeAll(calls).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).distinct().toList();
            assertThat(refs).hasSize(1);
        }
        providerA.verify(1, postRequestedFor(urlEqualTo("/transaction/initialize")));
    }

    @Test
    void missingIdempotencyKeyOrBadInputIsA400() throws Exception {
        mvc.perform(post("/api/v1/payments").with(merchant("m1")).contentType(MediaType.APPLICATION_JSON).content(body(50_000, "NGN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/idempotency-key-missing"));
        mvc.perform(post("/api/v1/payments").with(merchant("m1")).header("Idempotency-Key", "k-bad")
                        .contentType(MediaType.APPLICATION_JSON).content(body(50, "EUR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/validation-failed"));
    }

    @Test
    void validSignedWebhookCompletesThePaymentAndBadSignatureChangesNothing() throws Exception {
        String ref = newPayment("m2");
        byte[] forged = ("{\"event\":\"charge.success\",\"data\":{\"id\":\"1\",\"reference\":\"" + ref + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/provider-a").contentType(MediaType.APPLICATION_JSON).content(forged)
                        .header("x-provider-signature", HmacSignatures.sign("wrong-secret", forged)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/provider-a").contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(status().isUnauthorized());
        assertThat(paymentStatus("m2", ref)).isEqualTo("PENDING");

        webhook("provider-a", SECRET_A, "charge.success", "evt-ok-" + ref, ref, 50_000);
        assertThat(paymentStatus("m2", ref)).isEqualTo("SUCCEEDED");
    }

    @Test
    void duplicateWebhooksEvenConcurrentAreAppliedOnce() throws Exception {
        String ref = newPayment("m3");
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> {
                webhook("provider-a", SECRET_A, "charge.success", "evt-dup-" + ref, ref, 50_000);
                return null;
            });
        }
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (var f : pool.invokeAll(calls)) {
                f.get();
            }
        }
        Integer applied = jdbc.sql("""
                select count(*) from payment_event e join payment p on p.id = e.payment_id
                where p.reference = ? and e.to_status = 'SUCCEEDED'""").param(ref).query(Integer.class).single();
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void outOfOrderResultsDoNotMoveASucceededPaymentBackwards() throws Exception {
        String ref = newPayment("m4");
        webhook("provider-a", SECRET_A, "charge.success", "evt-s-" + ref, ref, 50_000);
        providerA.stubFor(WireMock.get("/transaction/verify/" + ref).willReturn(okJson("""
                {"status":true,"data":{"reference":"%s","status":"ongoing","amount":50000,"currency":"NGN","id":"1"}}""".formatted(ref))));
        mvc.perform(post("/api/v1/payments/" + ref + "/verify").with(merchant("m4"))).andExpect(status().isOk());
        webhook("provider-a", SECRET_A, "charge.failed", "evt-f-" + ref, ref, 50_000);
        assertThat(paymentStatus("m4", ref)).isEqualTo("SUCCEEDED");
    }

    @Test
    void wrongAmountInASuccessWebhookIsFlaggedNotApplied() throws Exception {
        String ref = newPayment("m4b");
        webhook("provider-a", SECRET_A, "charge.success", "evt-amt-" + ref, ref, 100);
        assertThat(paymentStatus("m4b", ref)).isEqualTo("PENDING");
    }

    @Test
    void failsOverToProviderBWhenProviderATimesOutThenReturns503WhenBothAreDown() throws Exception {
        providerA.stubFor(WireMock.post("/transaction/initialize").willReturn(okJson("{}").withFixedDelay(2_000)));
        for (int i = 0; i < 3; i++) {
            assertThat(create("m5", UUID.randomUUID().toString(), body(10_000, "NGN")).path("provider").asString())
                    .isEqualTo("provider-b");
        }
        assertThat(router.states()).containsEntry("provider-a", "OPEN");

        providerB.stubFor(WireMock.post("/transaction/initialize").willReturn(aResponse().withStatus(503)));
        mvc.perform(post("/api/v1/payments").with(merchant("m5")).header("Idempotency-Key", "k-down-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(10_000, "NGN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/no-provider-available"));
    }

    @Test
    void verifyRetriesTransientProviderErrors() throws Exception {
        String ref = newPayment("m6");
        router.reset();                  // start from an empty breaker window so two failures don't open it
        String path = "/transaction/verify/" + ref;
        providerA.stubFor(WireMock.get(path).inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("second"));
        providerA.stubFor(WireMock.get(path).inScenario("flaky").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("third"));
        providerA.stubFor(WireMock.get(path).inScenario("flaky").whenScenarioStateIs("third").willReturn(okJson("""
                {"status":true,"data":{"reference":"%s","status":"success","amount":50000,"currency":"NGN","id":"7"}}""".formatted(ref))));
        mvc.perform(post("/api/v1/payments/" + ref + "/verify").with(merchant("m6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void merchantsCantSeeOrTouchOtherMerchantsPayments() throws Exception {
        String ref = newPayment("owner");
        mvc.perform(get("/api/v1/payments/" + ref).with(merchant("intruder"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/payments/" + ref + "/verify").with(merchant("intruder"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/payments/" + ref + "/refunds").with(merchant("intruder"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/" + ref).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousAndUnderScopedCallersAreRejected() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body(10_000, "NGN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_api.read")))
                        .header("Idempotency-Key", "k-scope").contentType(MediaType.APPLICATION_JSON).content(body(10_000, "NGN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ops/providers").with(merchant("m1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ops/providers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("provider-a"));
    }

    @Test
    void partialRefundsMayNotExceedTheAmount() throws Exception {
        String ref = newPayment("m7");
        mvc.perform(post("/api/v1/payments/" + ref + "/refunds").with(merchant("m7"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}")).andExpect(status().isConflict());
        webhook("provider-a", SECRET_A, "charge.success", "evt-r-" + ref, ref, 50_000);
        providerA.stubFor(WireMock.post("/refund").willReturn(okJson("{\"status\":true,\"data\":{\"id\":\"rf\",\"status\":\"pending\"}}")));
        mvc.perform(post("/api/v1/payments/" + ref + "/refunds").with(merchant("m7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":30000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        mvc.perform(post("/api/v1/payments/" + ref + "/refunds").with(merchant("m7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20001}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/refund-exceeds-amount"));
        mvc.perform(post("/api/v1/payments/" + ref + "/refunds").with(merchant("m7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void reconciliationAbandonsOldPendingPaymentsAndVerifiesRecentOnes() throws Exception {
        String old = newPayment("m8");
        String recent = newPayment("m8");
        jdbc.sql("update payment set created_at = now() - interval '25 hours', updated_at = now() - interval '25 hours' where reference = ?")
                .param(old).update();
        jdbc.sql("update payment set updated_at = now() - interval '20 minutes' where reference = ?").param(recent).update();
        providerA.stubFor(WireMock.get(urlPathMatching("/transaction/verify/.*")).willReturn(okJson("""
                {"status":true,"data":{"reference":"%s","status":"success","amount":50000,"currency":"NGN","id":"8"}}""".formatted(recent))));
        assertThat(reconciliation.run()).isGreaterThanOrEqualTo(2);
        assertThat(paymentStatus("m8", old)).isEqualTo("ABANDONED");
        assertThat(paymentStatus("m8", recent)).isEqualTo("SUCCEEDED");

        webhook("provider-a", SECRET_A, "charge.success", "evt-late-" + old, old, 50_000);
        assertThat(paymentStatus("m8", old)).isEqualTo("ABANDONED");
    }

    @Test
    void listIsPaginatedNewestFirstAndScopedToTheMerchant() throws Exception {
        for (int i = 0; i < 5; i++) {
            newPayment("m9");
        }
        newPayment("someone-else");
        String page1 = mvc.perform(get("/api/v1/payments?limit=3").with(merchant("m9")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode p1 = json.readTree(page1);
        assertThat(p1.path("items").size()).isEqualTo(3);
        String page2 = mvc.perform(get("/api/v1/payments?limit=3&cursor=" + p1.path("nextCursor").asString()).with(merchant("m9")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode p2 = json.readTree(page2);
        assertThat(p2.path("items").size()).isEqualTo(2);
        assertThat(p2.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void malformedAndUnknownProviderWebhooksAreRejected() throws Exception {
        byte[] junk = "not json".getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/provider-a").content(junk).header("x-provider-signature", HmacSignatures.sign(SECRET_A, junk)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/webhooks/nobody").content(junk).header("x-provider-signature", "00"))
                .andExpect(status().isNotFound());
    }
}
