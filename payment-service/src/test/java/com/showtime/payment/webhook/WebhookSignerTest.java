package com.showtime.payment.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "payment-test-signing-secret-32-bytes-min!!!";

    @Test
    void signingIsDeterministicForTheSameBodyAndSecret() {
        WebhookSigner signer = new WebhookSigner(SECRET);
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.sign(body)).isEqualTo(signer.sign(body));
    }

    @Test
    void differentBodiesProduceDifferentSignatures() {
        WebhookSigner signer = new WebhookSigner(SECRET);

        String sigA = signer.sign("{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8));
        String sigB = signer.sign("{\"eventId\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(sigA).isNotEqualTo(sigB);
    }

    @Test
    void rejectsATooShortSecret() {
        assertThatThrownBy(() -> new WebhookSigner("short")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signatureIsLowercaseHexOfExpectedLength() {
        WebhookSigner signer = new WebhookSigner(SECRET);

        String signature = signer.sign("body".getBytes(StandardCharsets.UTF_8));

        assertThat(signature).matches("[0-9a-f]{64}"); // HMAC-SHA-256 -> 32 bytes -> 64 hex chars
    }
}
