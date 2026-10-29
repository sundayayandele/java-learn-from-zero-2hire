package ng.itlearning.pay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AccountController.class)
class AccountControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void aRepeatedHoldWithTheSameReferenceReservesOnlyOnce() {
        String hold = """
                {"reference": "TRF-1", "amount": 2500.00}""";
        assertThat(mvc.post().uri("/accounts/A1/holds").contentType(MediaType.APPLICATION_JSON).content(hold))
                .hasStatus(201);
        assertThat(mvc.post().uri("/accounts/A1/holds").contentType(MediaType.APPLICATION_JSON).content(hold))
                .hasStatus(200);
        assertThat(mvc.get().uri("/accounts/A1")).bodyJson()
                .extractingPath("$.available").asNumber().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(47500.00));
    }

    @Test
    void aHoldLargerThanTheBalanceIsRejectedWith422() {
        assertThat(mvc.post().uri("/accounts/A2/holds").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reference": "TRF-2", "amount": 9000.00}"""))
                .hasStatus(422);
    }
}
