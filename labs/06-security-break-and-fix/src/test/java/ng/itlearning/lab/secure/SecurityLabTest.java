package ng.itlearning.lab.secure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One failing test per planted vulnerability (bug 2 has two). Fix the application; don't weaken the tests. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)     // fresh in-memory data per test
class SecurityLabTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    static RequestPostProcessor customer(String name) {
        return jwt().jwt(j -> j.subject(name)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Test
    void bug1_aCustomerCannotReadSomeoneElsesAccount() throws Exception {
        mvc.perform(get("/api/accounts/1").with(customer("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/2").with(customer("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void bug2_searchIsNotInjectable() throws Exception {
        String body = mvc.perform(get("/api/beneficiaries").param("name", "' OR owner LIKE 'b").with(customer("alice")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Offshore Holdings");
    }

    @Test
    void bug2_errorsDoNotLeakSqlOrInternals() throws Exception {
        String body = mvc.perform(get("/api/beneficiaries").param("name", "'").with(customer("alice")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("sql").doesNotContainIgnoringCase("syntax")
                .doesNotContainIgnoringCase("beneficiary").doesNotContain("Exception");
    }

    @Test
    void bug3_customersCannotPromoteThemselves() throws Exception {
        mvc.perform(patch("/api/me").with(customer("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice A.\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice A."))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void bug4_adminEndpointsNeedTheAdminRole() throws Exception {
        mvc.perform(get("/api/admin/users").with(customer("alice"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(jwt().jwt(j -> j.subject("ada"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
    }

    @Test
    void bug5_passwordsAreStoredWithAnAdaptiveHash() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"chi\",\"displayName\":\"Chi\",\"password\":\"Correct-Horse-9\"}"))
                .andExpect(status().isCreated());
        String stored = jdbc.sql("select password_hash from app_user where username = 'chi'").query(String.class).single();
        assertThat(stored).startsWith("{").doesNotContain("Correct-Horse-9");
        assertThat(stored).doesNotStartWith("{MD5}").doesNotStartWith("{SHA-256}").doesNotStartWith("{noop}");
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("Correct-Horse-9", stored)).isTrue();
    }
}
