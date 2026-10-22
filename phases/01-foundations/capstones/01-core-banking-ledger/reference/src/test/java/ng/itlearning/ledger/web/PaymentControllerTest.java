package ng.itlearning.ledger.web;

import ng.itlearning.ledger.account.InsufficientFundsException;
import ng.itlearning.ledger.journal.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web slice only: validation and error mapping, with the service mocked. */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean LedgerService ledger;

    @Test
    void rejectsInvalidTransferWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fromAccount": "123", "toAccount": "1000000002", "amount": 10.555, "reference": "r 1"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(ledger);
    }

    @Test
    void mapsInsufficientFundsTo422() throws Exception {
        when(ledger.transfer(any())).thenThrow(
                new InsufficientFundsException("1000000001", new BigDecimal("500.00"), new BigDecimal("10.00")));
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fromAccount": "1000000001", "toAccount": "1000000002", "amount": 500.00, "reference": "r-1"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/ledger/insufficient-funds"))
                .andExpect(jsonPath("$.detail").value("Account ******0001 has insufficient funds"));
    }
}
