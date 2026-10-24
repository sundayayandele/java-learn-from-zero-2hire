package ng.itlearning.merchants.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheTokenSubject() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-42").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThat(CurrentUser.subject()).isEqualTo("user-42");
    }

    @Test
    void failsWithoutAnAuthenticatedCaller() {
        assertThatIllegalStateException().isThrownBy(CurrentUser::subject);
    }
}
