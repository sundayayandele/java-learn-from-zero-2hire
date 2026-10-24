package ng.itlearning.bills.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void mapsScopesAndKeycloakRealmRolesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-1")
                .claim("scope", "api.read")
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .build();
        var auth = SecurityConfig.jwtAuthenticationConverter().convert(jwt);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_api.read", "ROLE_CUSTOMER");
        assertThat(auth.getName()).isEqualTo("user-1");
    }

    @Test
    void tokenWithoutRealmRolesHasNoRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("svc").claim("scope", "").build();
        assertThat(SecurityConfig.realmRoles(jwt)).isEmpty();
    }
}
