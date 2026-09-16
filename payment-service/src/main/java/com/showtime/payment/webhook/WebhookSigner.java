package com.showtime.payment.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs outgoing webhook bodies with HMAC-SHA-256, hex-encoded — the exact
 * counterpart of Booking's {@code WebhookSignatureVerifier}. SHA-256 (not
 * SHA-1/MD5) per this workspace's cryptography guidelines; the shared
 * secret is read only from configuration/environment, never hardcoded.
 */
@Component
public class WebhookSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretKeyBytes;

    public WebhookSigner(@Value("${showtime.webhook.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("showtime.webhook.secret must be configured with at least 32 bytes");
        }
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("unable to compute webhook HMAC", ex);
        }
    }
}
