package ng.itlearning.openbanking;

import ng.itlearning.openbanking.seed.DemoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The brief's acceptance criteria against a real PostgreSQL with synthetic customers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenBankingFlowTests {

    static final String PROBLEMS = "https://problems.itlearning.ng/";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired DemoData data;
    @Autowired JdbcClient jdbc;

    DemoData.Customer ada;
    DemoData.Customer obi;

    @BeforeEach
    void customers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ada = data.customer("ada-" + suffix, "Ada Eze", List.of("CURRENT", "SAVINGS", "DOMICILIARY"), 120, suffix.hashCode());
        obi = data.customer("obi-" + suffix, "Obi Nwosu", List.of("CURRENT"), 20, suffix.hashCode() + 1);
    }

    static RequestPostProcessor tpp(String clientId) {
        return jwt().jwt(j -> j.claim("azp", clientId)).authorities(new SimpleGrantedAuthority("SCOPE_accounts"));
    }

    static RequestPostProcessor customer(DemoData.Customer c) {
        return jwt().jwt(j -> j.claim("preferred_username", c.username())).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    String createConsent(String tppId, String permissions, Instant expires) throws Exception {
        String body = """
                {"permissions":[%s],"expirationDateTime":"%s"}""".formatted(permissions, expires);
        String res = mvc.perform(post("/open-banking/v1/account-access-consents").with(tpp(tppId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AwaitingAuthorisation"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("consentId").asString();
    }

    void authorise(DemoData.Customer c, String consentId, String... accountIds) throws Exception {
        mvc.perform(post("/customer/v1/consents/" + consentId + "/authorise").with(customer(c))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new java.util.HashMap<>(java.util.Map.of("accountIds", List.of(accountIds))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Authorised"));
    }

    static final String ALL = "\"ReadAccountsDetail\",\"ReadBalances\",\"ReadTransactionsDetail\"";

    Instant in(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void fullFlowSharesOnlyTheChosenAccounts() throws Exception {
        String consent = createConsent("budgetapp", ALL, in(30));
        authorise(ada, consent, ada.accountIds().get(0), ada.accountIds().get(1));

        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")).header("x-consent-id", consent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountNumber").exists());
        mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(0) + "/balances").with(tpp("budgetapp"))
                        .header("x-consent-id", consent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("NGN"));
        mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(1) + "/transactions?limit=5").with(tpp("budgetapp"))
                        .header("x-consent-id", consent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(5))
                .andExpect(jsonPath("$.transactions[0].narration").exists())
                .andExpect(jsonPath("$.nextCursor").exists());

        mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(2) + "/balances").with(tpp("budgetapp"))
                        .header("x-consent-id", consent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "account-not-in-consent"));
        Integer denied = jdbc.sql("select count(*) from access_log where consent_id = ? and outcome = 'DENIED' and reason = 'account-not-in-consent'")
                .param(consent).query(Integer.class).single();
        assertThat(denied).isEqualTo(1);
    }

    @Test
    void anotherTppsConsentAndMissingPermissionsAreRefused() throws Exception {
        String consent = createConsent("budgetapp", "\"ReadAccountsBasic\"", in(30));
        authorise(ada, consent, ada.accountIds().get(0));
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("someone-else")).header("x-consent-id", consent))
                .andExpect(status().isNotFound());
        mvc.perform(get("/open-banking/v1/account-access-consents/" + consent).with(tpp("someone-else")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(0) + "/balances").with(tpp("budgetapp"))
                        .header("x-consent-id", consent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "permission-not-granted"));
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")).header("x-consent-id", consent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").doesNotExist());
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorisedExpiredAndRevokedConsentsAreRefused() throws Exception {
        String waiting = createConsent("budgetapp", ALL, in(30));
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")).header("x-consent-id", waiting))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value(PROBLEMS + "consent-not-authorised"));

        String expiring = createConsent("budgetapp", ALL, in(30));
        authorise(ada, expiring, ada.accountIds().get(0));
        jdbc.sql("update consent set expires_at = now() - interval '1 minute' where id = ?").param(expiring).update();
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")).header("x-consent-id", expiring))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value(PROBLEMS + "consent-expired"));
        mvc.perform(get("/open-banking/v1/account-access-consents/" + expiring).with(tpp("budgetapp")))
                .andExpect(jsonPath("$.status").value("Expired"));

        String revoked = createConsent("budgetapp", ALL, in(30));
        authorise(ada, revoked, ada.accountIds().get(0));
        mvc.perform(post("/customer/v1/consents/" + revoked + "/revoke").with(customer(ada))).andExpect(status().isNoContent());
        mvc.perform(get("/open-banking/v1/accounts").with(tpp("budgetapp")).header("x-consent-id", revoked))
                .andExpect(status().isForbidden());
        mvc.perform(get("/customer/v1/consents").with(customer(ada)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void customersCanOnlyShareTheirOwnAccounts() throws Exception {
        String consent = createConsent("budgetapp", ALL, in(30));
        mvc.perform(post("/customer/v1/consents/" + consent + "/authorise").with(customer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"" + ada.accountIds().get(0) + "\",\"" + obi.accountIds().get(0) + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "not-account-owner"));
        mvc.perform(get("/open-banking/v1/account-access-consents/" + consent).with(tpp("budgetapp")))
                .andExpect(jsonPath("$.status").value("AwaitingAuthorisation"))
                .andExpect(jsonPath("$.accountIds.length()").value(0));
        mvc.perform(post("/customer/v1/consents/" + consent + "/reject").with(customer(obi))).andExpect(status().isNoContent());
        mvc.perform(post("/customer/v1/consents/" + consent + "/revoke").with(customer(ada))).andExpect(status().isNotFound());
        mvc.perform(post("/customer/v1/consents/" + consent + "/reject").with(customer(obi))).andExpect(status().isConflict());
    }

    @Test
    void basicPermissionHidesCounterpartyAndNarration() throws Exception {
        String consent = createConsent("budgetapp", "\"ReadTransactionsBasic\"", in(30));
        authorise(ada, consent, ada.accountIds().get(0));
        mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(0) + "/transactions?limit=3").with(tpp("budgetapp"))
                        .header("x-consent-id", consent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].amount").exists())
                .andExpect(jsonPath("$.transactions[0].narration").doesNotExist())
                .andExpect(jsonPath("$.transactions[0].counterpartyName").doesNotExist());
    }

    @Test
    void transactionsOutsideTheConsentWindowAreNeverReturned() throws Exception {
        Instant windowStart = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"permissions":["ReadTransactionsDetail"],"expirationDateTime":"%s","transactionFromDateTime":"%s"}"""
                .formatted(in(30), windowStart);
        String res = mvc.perform(post("/open-banking/v1/account-access-consents").with(tpp("budgetapp"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String consent = json.readTree(res).path("consentId").asString();
        authorise(ada, consent, ada.accountIds().get(0));
        String page = mvc.perform(get("/open-banking/v1/accounts/" + ada.accountIds().get(0)
                                + "/transactions?limit=200&fromBookingDateTime=2020-01-01T00:00:00Z").with(tpp("budgetapp"))
                        .header("x-consent-id", consent).header("x-fapi-customer-ip-address", "10.0.0.1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode txns = json.readTree(page).path("transactions");
        for (JsonNode t : txns) {
            assertThat(Instant.parse(t.path("bookingDateTime").asString())).isAfterOrEqualTo(windowStart);
        }
        Integer olderExist = jdbc.sql("select count(*) from txn where account_id = ? and booked_at < ?")
                .params(ada.accountIds().get(0), java.sql.Timestamp.from(windowStart)).query(Integer.class).single();
        assertThat(olderExist).isPositive();
    }

    @Test
    void fifthUnattendedCallInADayIsRateLimited() throws Exception {
        String consent = createConsent("budgetapp", ALL, in(30));
        authorise(ada, consent, ada.accountIds().get(0));
        String url = "/open-banking/v1/accounts/" + ada.accountIds().get(0) + "/balances";
        for (int i = 0; i < 4; i++) {
            mvc.perform(get(url).with(tpp("budgetapp")).header("x-consent-id", consent)).andExpect(status().isOk());
        }
        mvc.perform(get(url).with(tpp("budgetapp")).header("x-consent-id", consent))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Customer-present calls (the customer is using the TPP app right now) are not limited.
        mvc.perform(get(url).with(tpp("budgetapp")).header("x-consent-id", consent).header("x-fapi-customer-ip-address", "10.0.0.1"))
                .andExpect(status().isOk());
    }

    @Test
    void consentValidationAndWithdrawal() throws Exception {
        mvc.perform(post("/open-banking/v1/account-access-consents").with(tpp("budgetapp")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ReadBalances\"],\"expirationDateTime\":\"" + in(120) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/open-banking/v1/account-access-consents").with(tpp("budgetapp")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[],\"expirationDateTime\":\"" + in(10) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/open-banking/v1/account-access-consents").with(tpp("budgetapp")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ReadBalances\"],\"expirationDateTime\":\"" + in(10)
                                + "\",\"transactionFromDateTime\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        String consent = createConsent("budgetapp", ALL, in(30));
        mvc.perform(delete("/open-banking/v1/account-access-consents/" + consent).with(tpp("budgetapp"))).andExpect(status().isNoContent());
        mvc.perform(get("/open-banking/v1/account-access-consents/" + consent).with(tpp("budgetapp")))
                .andExpect(jsonPath("$.status").value("Revoked"));
    }

    @Test
    void everyResponseCarriesACorrelationIdEvenWhenRejected() throws Exception {
        mvc.perform(get("/open-banking/v1/accounts")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));
        mvc.perform(get("/customer/v1/consents").with(customer(ada)).header("X-Correlation-Id", "trace-0000-1111"))
                .andExpect(header().string("X-Correlation-Id", "trace-0000-1111"));
    }

    @Test
    void everyEndpointNeedsTheRightCaller() throws Exception {
        mvc.perform(get("/open-banking/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/customer/v1/consents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/open-banking/v1/accounts").with(customer(ada))).andExpect(status().isForbidden());
        mvc.perform(get("/customer/v1/consents").with(tpp("budgetapp"))).andExpect(status().isForbidden());
        mvc.perform(get("/ops/v1/access-log").with(customer(ada))).andExpect(status().isForbidden());
        mvc.perform(get("/ops/v1/access-log?limit=2").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries").isArray());
        mvc.perform(get("/customer/v1/consents").with(jwt().jwt(j -> j.claim("preferred_username", "stranger"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
}
