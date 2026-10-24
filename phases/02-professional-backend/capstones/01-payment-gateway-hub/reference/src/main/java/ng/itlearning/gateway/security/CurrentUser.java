package ng.itlearning.gateway.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The caller's identity from the validated JWT. Use it for ownership checks (topic 6.3: never trust ids in the request). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Merchant applications use client credentials; their identity is the token's subject (the service account). */
    public static String subject() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        throw new IllegalStateException("No authenticated JWT caller");
    }
}
