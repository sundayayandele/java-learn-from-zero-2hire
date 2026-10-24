package ng.itlearning.lab.rates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RatesApplicationTests {

    @Autowired MockMvc mvc;

    @Test
    void returnsARate() throws Exception {
        mvc.perform(get("/api/rates/USD")).andExpect(status().isOk()).andExpect(jsonPath("$.ngn").value("1532.40"));
    }
}
