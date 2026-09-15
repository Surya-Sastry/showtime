package com.showtime.identity.security;

import com.showtime.identity.domain.Role;
import com.showtime.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Issues and validates short-lived JWT access tokens signed with HMAC-SHA-256.
 * The signing key is provided only via configuration (environment variable in
 * practice — see .env.example); it is never hardcoded and never logged.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSigningKey();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "showtime.jwt.signing-key must be configured with at least 32 bytes; "
                            + "generate it locally and never commit it (see .env.example)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(properties.getIssuer())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates signature, issuer, and expiry, then returns the decoded
     * principal. Throws {@link JwtException} on any failure — callers must
     * treat that as an authentication failure, not surface parsing details.
     */
    public JwtPrincipal validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        String roleClaim = claims.get(CLAIM_ROLE, String.class);
        if (subject == null || roleClaim == null) {
            throw new JwtException("token is missing required claims");
        }
        return new JwtPrincipal(java.util.UUID.fromString(subject), Role.valueOf(roleClaim));
    }

    public record JwtPrincipal(java.util.UUID userId, Role role) {
    }
}
