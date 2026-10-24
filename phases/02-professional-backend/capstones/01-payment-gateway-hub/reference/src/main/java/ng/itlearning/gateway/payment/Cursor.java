package ng.itlearning.gateway.payment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** An opaque keyset cursor: the (created_at, id) of the last row on the previous page. */
public record Cursor(Instant createdAt, long id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String token) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\|");
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new GatewayException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid-cursor", "The cursor is not valid.");
        }
    }
}
