package com.showtime.booking.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the mock payment service's webhook signatures. HMAC-SHA-256 over
 * the raw request body, hex-encoded, per the README's security requirement
 * ("Sign mock-payment callbacks with HMAC-SHA-256 and compare signatures in
 * constant time"). SHA-256 is used (not SHA-1/MD5) and comparison uses
 * {@link MessageDigest#isEqual} specifically because it runs in constant
 * time regardless of where the strings first differ, closing the timing
 * side-channel a naive {@code String.equals} would leave open.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretKeyBytes;

    public WebhookSignatureVerifier(@Value("${showtime.webhook.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("showtime.webhook.secret must be configured with at least 32 bytes");
        }
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(byte[] rawBody, String providedSignatureHex) {
        if (providedSignatureHex == null || providedSignatureHex.isBlank()) {
            return false;
        }
        byte[] expected = computeHmac(rawBody);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedSignatureHex.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private byte[] computeHmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("unable to compute webhook HMAC", ex);
        }
    }
}
