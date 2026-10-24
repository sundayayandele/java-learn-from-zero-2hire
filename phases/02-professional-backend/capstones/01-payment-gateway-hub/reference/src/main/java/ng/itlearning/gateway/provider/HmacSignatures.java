package ng.itlearning.gateway.provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA512 webhook signatures, compared in constant time (topic 6.4). */
public final class HmacSignatures {

    private HmacSignatures() {
    }

    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    public static boolean valid(String secret, byte[] body, String signature) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
