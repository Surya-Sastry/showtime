package com.showtime.catalog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtVerifier {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtVerifier(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSigningKey();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "showtime.jwt.signing-key must be configured with at least 32 bytes");
        }
        this.signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Principal verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new Principal(UUID.fromString(claims.getSubject()), claims.get("role", String.class));
    }

    public record Principal(UUID userId, String role) {
    }
}
