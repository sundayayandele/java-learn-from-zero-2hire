package ng.itlearning.gateway.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignaturesTest {

    private final byte[] body = "{\"event\":\"charge.success\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsTheCorrectSignatureInAnyCase() {
        String sig = HmacSignatures.sign("s3cret", body);
        assertThat(sig).hasSize(128);
        assertThat(HmacSignatures.valid("s3cret", body, sig)).isTrue();
        assertThat(HmacSignatures.valid("s3cret", body, sig.toUpperCase())).isTrue();
    }

    @Test
    void rejectsWrongSecretChangedBodyAndMissingValues() {
        String sig = HmacSignatures.sign("s3cret", body);
        assertThat(HmacSignatures.valid("other", body, sig)).isFalse();
        assertThat(HmacSignatures.valid("s3cret", "{\"event\":\"charge.failed\"}".getBytes(StandardCharsets.UTF_8), sig)).isFalse();
        assertThat(HmacSignatures.valid("s3cret", body, null)).isFalse();
        assertThat(HmacSignatures.valid("", body, sig)).isFalse();
    }
}
