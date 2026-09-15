package com.showtime.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showtime.identity.domain.Role;
import com.showtime.identity.domain.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SIGNING_KEY = "a-test-signing-key-that-is-at-least-32-bytes-long";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSigningKey(SIGNING_KEY);
        properties.setIssuer("showtime-identity");
        properties.setAccessTokenTtlMinutes(15);
        jwtService = new JwtService(properties);
    }

    @Test
    void issuedTokenValidatesBackToTheSameUserAndRole() {
        User user = User.register("viewer@example.com", "irrelevant-hash", Role.CUSTOMER);
        setId(user);

        String token = jwtService.issueAccessToken(user);
        JwtService.JwtPrincipal principal = jwtService.validate(token);

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-32-byte-plus-key!!".getBytes());
        String forged = Jwts.builder()
                .subject(java.util.UUID.randomUUID().toString())
                .issuer("showtime-identity")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validate(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes());
        String expired = Jwts.builder()
                .subject(java.util.UUID.randomUUID().toString())
                .issuer("showtime-identity")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validate(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenFromADifferentIssuer() {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes());
        String wrongIssuer = Jwts.builder()
                .subject(java.util.UUID.randomUUID().toString())
                .issuer("some-other-service")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validate(wrongIssuer)).isInstanceOf(JwtException.class);
    }

    private static void setId(User user) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, java.util.UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
