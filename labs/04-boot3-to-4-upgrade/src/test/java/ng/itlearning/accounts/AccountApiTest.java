package ng.itlearning.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountApiTest.Containers.class)
class AccountApiTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:17-alpine");
        }
    }

    @Autowired MockMvc mvc;
    @MockBean NotificationClient notifications;

    /** JSON contract: this exact output must survive the upgrade. */
    @Test
    void accountJsonContract() throws Exception {
        mvc.perform(get("/api/accounts/0123456789"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"number":"0123456789","holderName":"Ada Okafor","balance":150000.50,"openedOn":"2024-03-01"}
                        """, true));
        verify(notifications).accountViewed("0123456789");
    }

    @Test
    void unknownAccountIs404() throws Exception {
        mvc.perform(get("/api/accounts/9999999999")).andExpect(status().isNotFound());
    }
}
