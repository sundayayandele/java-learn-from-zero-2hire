package ng.itlearning.openbanking.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The caller's identity from the validated JWT. Use it for ownership checks (topic 6.3: never trust ids in the request). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The OAuth client that obtained the token (Keycloak's {@code azp}): for TPPs this is their identity. */
    public static String clientId() {
        return claim("azp");
    }

    /** The bank customer's username ({@code preferred_username}; usernames can't be edited in the itl realm). */
    public static String username() {
        return claim("preferred_username");
    }

    private static String claim(String name) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && token.getToken().getClaimAsString(name) != null) {
            return token.getToken().getClaimAsString(name);
        }
        throw new IllegalStateException("No " + name + " claim in the caller's token");
    }

    public static String subject() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        throw new IllegalStateException("No authenticated JWT caller");
    }
}
