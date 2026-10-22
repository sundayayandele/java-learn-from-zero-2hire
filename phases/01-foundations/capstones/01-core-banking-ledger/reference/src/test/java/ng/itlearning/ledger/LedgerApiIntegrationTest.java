package ng.itlearning.ledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end flows against a real PostgreSQL (Testcontainers), through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void depositAndTransferPostBalancedJournalsWithFee() throws Exception {
        String a = open("Ada Okafor", null);
        String b = open("Bola Ade", null);
        deposit(a, "50000.00").andExpect(status().isCreated());

        transfer(a, b, "20000.00", ref()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee").value(26.88))
                .andExpect(jsonPath("$.replayed").value(false));

        balance(a, 29973.12);
        balance(b, 20000.00);
        mvc.perform(get("/api/v1/ledger/trial-balance")).andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(get("/api/v1/ledger/consistency")).andExpect(jsonPath("$.consistent").value(true));
    }

    @Test
    void overdraftIsRejectedAndChangesNothing() throws Exception {
        String a = open("Chidi Eze", null);
        String b = open("Dayo Bello", null);
        deposit(a, "1000.00");

        transfer(a, b, "995.00", ref()).andExpect(status().isUnprocessableContent())   // 995 + 10.75 fee > 1000
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/ledger/insufficient-funds"));
        balance(a, 1000.00);
        balance(b, 0.00);
    }

    @Test
    void sameReferenceAndPayloadIsReplayedNotPostedTwice() throws Exception {
        String a = open("Emeka Obi", null);
        String b = open("Funke Ojo", null);
        deposit(a, "10000.00");
        String reference = ref();

        transfer(a, b, "1000.00", reference).andExpect(status().isCreated());
        transfer(a, b, "1000.00", reference).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        transfer(a, b, "2000.00", reference).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/ledger/duplicate-reference"));

        balance(b, 1000.00);
        Integer journals = jdbc.sql("select count(*) from journal where reference = :r").param("r", reference)
                .query(Integer.class).single();
        assertThat(journals).isEqualTo(1);
    }

    @Test
    void frozenAccountsCanNeitherSendNorReceive() throws Exception {
        String a = open("Gbenga Ali", null);
        String b = open("Hauwa Musa", null);
        deposit(a, "5000.00");
        mvc.perform(post("/api/v1/accounts/{n}/freeze", b)).andExpect(status().isNoContent());

        transfer(a, b, "100.00", ref()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/ledger/account-not-active"));
    }

    @Test
    void dailyLimitCountsAmountPlusFee() throws Exception {
        String a = open("Ifeoma Nwosu", "10000.00");
        String b = open("Jide Cole", null);
        deposit(a, "50000.00");

        transfer(a, b, "6000.00", ref()).andExpect(status().isCreated());                 // 6026.88 used
        transfer(a, b, "4000.00", ref()).andExpect(status().isUnprocessableContent())      // +4010.75 > 10,000
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/ledger/daily-limit-exceeded"));
    }

    @Test
    void reversalRestoresBalancesOnlyOnce() throws Exception {
        String a = open("Kemi Ade", null);
        String b = open("Lanre Ola", null);
        deposit(a, "10000.00");
        String reference = ref();
        transfer(a, b, "3000.00", reference);

        mvc.perform(post("/api/v1/transfers/{r}/reversal", reference)).andExpect(status().isCreated());
        balance(a, 10000.00);
        balance(b, 0.00);
        mvc.perform(post("/api/v1/transfers/{r}/reversal", reference)).andExpect(status().isConflict());
    }

    @Test
    void statementClosingBalanceEqualsOpeningPlusMovements() throws Exception {
        String a = open("Musa Bala", null);
        String b = open("Ngozi Ike", null);
        deposit(a, "8000.00");
        transfer(a, b, "500.00", ref());
        transfer(a, b, "700.00", ref());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Lagos"));

        String json = mvc.perform(get("/api/v1/accounts/{n}/statement", a)
                        .param("from", today.minusDays(1).toString()).param("to", today.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(json, "$.totalLines")).isEqualTo(3);
        assertThat(JsonPath.<Number>read(json, "$.openingBalance").doubleValue()).isEqualTo(0.0);
        assertThat(JsonPath.<Number>read(json, "$.closingBalance").doubleValue()).isEqualTo(8000 - 510.75 - 710.75);
        assertThat(JsonPath.<Number>read(json, "$.lines[2].runningBalance").doubleValue()).isEqualTo(8000 - 510.75 - 710.75);
    }

    @Test
    void postingsAreImmutableAtTheDatabaseLevel() {
        assertThatThrownBy(() -> jdbc.sql("update posting set amount = amount + 1").update())
                .hasMessageContaining("postings are immutable");
    }

    @Test
    void validationErrorsAreProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"holderName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("holderName"));
        mvc.perform(get("/api/v1/accounts/{n}", "1999999999")).andExpect(status().isNotFound());
    }

    private String open(String holder, String dailyLimit) throws Exception {
        String body = dailyLimit == null
                ? "{\"holderName\": \"%s\"}".formatted(holder)
                : "{\"holderName\": \"%s\", \"dailyLimit\": %s}".formatted(holder, dailyLimit);
        String json = mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.number");
    }

    private ResultActions deposit(String account, String amount) throws Exception {
        return mvc.perform(post("/api/v1/deposits").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountNumber": "%s", "amount": %s, "reference": "%s"}""".formatted(account, amount, ref())));
    }

    private ResultActions transfer(String from, String to, String amount, String reference) throws Exception {
        return mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                {"fromAccount": "%s", "toAccount": "%s", "amount": %s, "reference": "%s", "narration": "test"}"""
                .formatted(from, to, amount, reference)));
    }

    private void balance(String account, double expected) throws Exception {
        mvc.perform(get("/api/v1/accounts/{n}", account)).andExpect(jsonPath("$.balance").value(expected));
    }

    private static String ref() {
        return "T-" + UUID.randomUUID();
    }
}
