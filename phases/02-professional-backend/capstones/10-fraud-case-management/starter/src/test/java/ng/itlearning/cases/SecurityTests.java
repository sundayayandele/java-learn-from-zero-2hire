package ng.itlearning.cases;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The security baseline: anonymous callers are rejected, health and API docs are public, actuator needs OPS. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityTests {

    @Autowired MockMvc mvc;

    @Test
    void anonymousRequestIsRejectedWithBearerChallenge() throws Exception {
        mvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void prometheusNeedsOpsRole() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedCallerReachesTheApi() throws Exception {
        mvc.perform(get("/does-not-exist").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
