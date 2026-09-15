package com.showtime.booking.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "webhook-test-secret-key-32-bytes-minimum!!";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET);

    @Test
    void acceptsACorrectlySignedBody() throws Exception {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body, SECRET);

        assertThat(verifier.isValid(body, signature)).isTrue();
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        byte[] originalBody = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(originalBody, SECRET);
        byte[] tamperedBody = "{\"eventId\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(tamperedBody, signature)).isFalse();
    }

    @Test
    void rejectsASignatureFromTheWrongSecret() throws Exception {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body, "a-completely-different-secret-key-value!!");

        assertThat(verifier.isValid(body, signature)).isFalse();
    }

    @Test
    void rejectsABlankOrMalformedSignature() {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(body, "")).isFalse();
        assertThat(verifier.isValid(body, "not-hex!!")).isFalse();
    }

    @Test
    void constructorRejectsATooShortSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WebhookSignatureVerifier("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body);
        return java.util.HexFormat.of().formatHex(digest);
    }
}
